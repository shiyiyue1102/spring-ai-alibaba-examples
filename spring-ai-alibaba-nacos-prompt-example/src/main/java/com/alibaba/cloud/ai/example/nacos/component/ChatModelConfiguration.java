package com.alibaba.cloud.ai.example.nacos.component;


import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.ResolvedConnectionProperties;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.annotation.NacosConfig;
import com.alibaba.nacos.api.config.listener.Listener;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionUtils.resolveConnectionProperties;

@Component
@ConditionalOnProperty(name = "spring.ai.nacos.proxy", havingValue = "true")
public class ChatModelConfiguration {

	@Bean
	public ChatClient chatClient(ChatModel chatModel) throws Exception {
		String config = NacosConfigManager.getInstance().getConfigService()
				.getConfig("agent-agent001-prompt.md", "nacos-ai-agent", 3000L);
		ChatClient chatClient = ChatClient.builder(chatModel).defaultSystem(config).build();
		registerPrompt(chatClient);
		return chatClient;
	}

	@Bean
	public DashScopeChatModel dashscopeChatModel(
			RetryTemplate retryTemplate,
			ToolCallingManager toolCallingManager,
			DashScopeChatProperties chatProperties,
			ResponseErrorHandler responseErrorHandler,
			DashScopeConnectionProperties commonProperties,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> dashscopeToolExecutionEligibilityPredicate
	) {

		var dashscopeApi = dashscopeChatApi(
				commonProperties,
				chatProperties,
				restClientBuilderProvider.getIfAvailable(RestClient::builder),
				webClientBuilderProvider.getIfAvailable(WebClient::builder),
				responseErrorHandler,
				"chat"
		);

		var dashscopeModel = DashScopeChatModel.builder()
				.dashScopeApi(dashscopeApi)
				.retryTemplate(retryTemplate)
				.toolCallingManager(toolCallingManager)
				.defaultOptions(chatProperties.getOptions())
				.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
				.toolExecutionEligibilityPredicate(
						dashscopeToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
				.build();

		observationConvention.ifAvailable(dashscopeModel::setObservationConvention);

		return dashscopeModel;
	}

	private DashScopeApi dashscopeChatApi(
			DashScopeConnectionProperties commonProperties,
			DashScopeChatProperties chatProperties,
			RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler,
			String modelType
	) {

		ResolvedConnectionProperties resolved = resolveConnectionProperties(
				commonProperties,
				chatProperties,
				modelType
		);

		return DashScopeApi.builder()
				.apiKey(resolved.apiKey())
				.headers(resolved.headers())
				.baseUrl(resolved.baseUrl())
				.webClientBuilder(webClientBuilder)
				.workSpaceId(resolved.workspaceId())
				.restClientBuilder(restClientBuilder)
				.responseErrorHandler(responseErrorHandler)
				.build();
	}

	private void registerPrompt(ChatClient chatClient) throws Exception {
		NacosConfigManager.getInstance().getConfigService()
				.addListener("agent-agent001-prompt.md", "nacos-ai-agent", new Listener() {
					@Override
					public Executor getExecutor() {
						return null;
					}

					@Override
					public void receiveConfigInfo(String configInfo) {
						Class<?> chatClientClass = chatClient.getClass();
						Field defaultChatClientRequest = null;
						try {
							defaultChatClientRequest = chatClientClass.getDeclaredField("defaultChatClientRequest");

							defaultChatClientRequest.setAccessible(true);
							Object defaultChatClientRequestSpec = defaultChatClientRequest.get(chatClient);
							// 获取 DefaultChatClientRequestSpec 类
							Class<?> defaultChatClientRequestSpecClass = defaultChatClientRequestSpec.getClass();

							// 获取 systemText 字段
							Field systemTextField = defaultChatClientRequestSpecClass.getDeclaredField("systemText");

							// 设置字段为可访问
							systemTextField.setAccessible(true);

							// 设置 systemText 字段的值
							systemTextField.set(defaultChatClientRequestSpec, configInfo);
						}
						catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				});

	}
}
