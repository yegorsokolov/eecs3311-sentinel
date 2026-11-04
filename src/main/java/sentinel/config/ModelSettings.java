package sentinel.config;

import java.net.URI;
import java.util.Properties;

/** Provider-neutral settings. Never include the credential in diagnostic text. */
public record ModelSettings(String provider, URI baseUrl, String model, String apiKey,
                            boolean jsonMode, String tokenField) {
    public ModelSettings {
        if (!"ollama".equals(provider) && !"openai-compatible".equals(provider))
            throw new ConfigurationException("SENTINEL_LLM_PROVIDER must be ollama or openai-compatible.");
        if (baseUrl == null || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null || baseUrl.getPort() == 0 || baseUrl.getPort() > 65535)
            throw new ConfigurationException("Model base URL requires HTTP(S), a valid host/port and no credentials, query or fragment.");
        if (model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*"))
            throw new ConfigurationException("Model name is invalid.");
        apiKey = apiKey == null ? "" : apiKey;
        if (!apiKey.isEmpty() && (!apiKey.matches("[!-~]+") || apiKey.length() > 8192))
            throw new ConfigurationException("API key must be a single printable ASCII token.");
        if (!apiKey.isEmpty() && !"https".equalsIgnoreCase(baseUrl.getScheme())
                && !java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(baseUrl.getHost()))
            throw new ConfigurationException("API keys require HTTPS except for loopback development endpoints.");
        if (!"max_tokens".equals(tokenField) && !"max_completion_tokens".equals(tokenField))
            throw new ConfigurationException("Token field must be max_tokens or max_completion_tokens.");
        if ("ollama".equals(provider) && !apiKey.isEmpty())
            throw new ConfigurationException("Local Ollama needs no API key; use openai-compatible for authenticated providers.");
    }

    static ModelSettings from(Properties settings, URI ollamaUrl, String ollamaModel) {
        String provider = settings.getProperty("llm.provider", "ollama");
        String url = settings.getProperty("llm.url");
        if ("openai-compatible".equals(provider) && url == null)
            throw new ConfigurationException("Set SENTINEL_LLM_BASE_URL for the selected provider, including its API version path.");
        String jsonMode = settings.getProperty("llm.jsonMode", "true");
        if (!jsonMode.equals("true") && !jsonMode.equals("false"))
            throw new ConfigurationException("SENTINEL_LLM_JSON_MODE must be true or false.");
        URI endpoint;
        try { endpoint = url == null ? ollamaUrl : URI.create(url); }
        catch (IllegalArgumentException ex) { throw new ConfigurationException("Invalid model base URL."); }
        return new ModelSettings(provider, endpoint, settings.getProperty("llm.model", ollamaModel),
                settings.getProperty("llm.apiKey", ""), Boolean.parseBoolean(jsonMode),
                settings.getProperty("llm.tokenField", "max_tokens"));
    }

    @Override public String toString() {
        return "ModelSettings[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                + ", apiKey=" + (apiKey.isEmpty() ? "absent" : "[REDACTED]") + "]";
    }
}
