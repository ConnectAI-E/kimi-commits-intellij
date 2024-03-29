package com.github.connectai_e.kimi.commits.intellij.settings

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.aallam.openai.client.ProxyConfig
import com.github.connectai_e.kimi.commits.intellij.AICommitsUtils
import com.github.connectai_e.kimi.commits.intellij.notifications.Notification
import com.github.connectai_e.kimi.commits.intellij.notifications.sendNotification
import com.github.connectai_e.kimi.commits.intellij.settings.prompt.Prompt
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.*
import kotlin.time.Duration.Companion.seconds

@State(
    name = AppSettings.SERVICE_NAME,
    storages = [Storage("AICommit.xml")]
)
class AppSettings : PersistentStateComponent<AppSettings> {

    private val openAITokenTitle = "KimiAIToken"
    private var hits = 0

    @OptionTag(converter = LocaleConverter::class)
    var locale: Locale = Locale.ENGLISH

    var requestSupport = true
    var lastVersion: String? = null
    var openAIHost = "https://api.moonshot.cn/v1/"
    var openAIHosts = mutableSetOf("https://api.moonshot.cn/v1/", OpenAIHost.OpenAI.baseUrl)
    var openAISocketTimeout = "30"
    var proxyUrl: String? = null
    var msgConsistency = true

    var prompts = initPrompts()
    var currentPrompt = prompts["conventional"] ?: prompts.values.first()

    var openAIModelId = "moonshot-v1-8k"
    var openAIModelIds = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
    var openAITemperature = "0.3"

    var appExclusions: Set<String> = setOf()

    companion object {
        const val SERVICE_NAME = "com.github.connectai_e.kimi.commits.intellij.settings.AppSettings"
        val instance: AppSettings
            get() = ApplicationManager.getApplication().getService(AppSettings::class.java)
    }

    fun saveKimiAIToken(token: String) {
        try {
            PasswordSafe.instance.setPassword(getCredentialAttributes(openAITokenTitle), token)
        } catch (e: Exception) {
            sendNotification(Notification.unableToSaveToken())
        }
    }

    fun getKimiAIConfig(): OpenAIConfig {
        val token = getKimiAIToken() ?: throw Exception("KimiAI Token is not set.")
        return OpenAIConfig(
            token,
            host = openAIHost.takeIf { it.isNotBlank() }?.let { OpenAIHost(it) } ?: OpenAIHost.OpenAI,
            proxy = proxyUrl?.takeIf { it.isNotBlank() }?.let { ProxyConfig.Http(it) },
            timeout = Timeout(socket = openAISocketTimeout.toInt().seconds)
        )
    }

    fun getKimiAIToken(): String? {
        val credentialAttributes = getCredentialAttributes(openAITokenTitle)
        val credentials: Credentials = PasswordSafe.instance.get(credentialAttributes) ?: return null
        return credentials.getPasswordAsString()
    }

    private fun getCredentialAttributes(title: String): CredentialAttributes {
        return CredentialAttributes(
            title,
            null,
            this.javaClass,
            false
        )
    }

    override fun getState() = this

    override fun loadState(state: AppSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun recordHit() {
        hits++
        if (requestSupport && (hits == 50 || hits % 100 == 0)) {
            sendNotification(Notification.star())
        }
    }

    fun isPathExcluded(path: String): Boolean {
        return AICommitsUtils.matchesGlobs(path, appExclusions)
    }

    private fun initPrompts() = mutableMapOf(
        // feat: generate unique UUIDs for game objects on Mine game start
        "conventional" to Prompt(
                "Conventional",
                "Prompt for commit message in the conventional commit convention.",
                """
                    Write a commit message in the conventional commit convention

                    The commit message should be structured as follows:
                    <type>[optional scope]: <description>
                    [optional body]
                    [optional footer(s)]
                    
                    right msg1:
                    docs: correct spelling of CHANGELOG
                    right msg2:
                    feat(lang): add polish language
                    right msg3:
                    fix: prevent racing of requests
                    right msg4:
                    feat(api)!: send an email to the customer when a product is shipped
                    wrong msg1:
                    Add .idea config files and a new document (main)
                     
                    I'll send you an output of 'git diff --staged' command, and you convert it into a commit message.
                    remember these：
                    - now branch is {branch}
                    - Lines must not be longer than 74 characters.
                    - Use {locale} language to answer.
               
                      
               
                    diff detail is below：

                    {diff}
                    give me git msg:
                """.trimIndent(),false
        ),
        "consistent" to Prompt(
                "Consistent",
                "Predict commit message format based on the previous commit messages.",
                """
                   I'll send you an output of 'git diff --staged' command, and you convert it into a commit message.
                   remember these：
                   - now branch is {branch}
                   - Determine the language to be used based on the given historical commit msg
                   - Based on the given historical commit msg, summarize the format, specifications, tone and intonation of the msg
                   - Imitate the language and style of historical git msg to write new git msg
                   - Lines must not be longer than 74 characters.
                   - Generate the appropriate git commit msg directly for me without any other unnecessary explanations


                   historical git msg is below
                   ------------------------
                   {history}


                   git diff detail is below
                   ------------------------
                   {diff}


                   so, git msg content is
                   -----------------------
                """.trimIndent(),
                false
        ),
    )

    class LocaleConverter : Converter<Locale>() {
        override fun toString(value: Locale): String? {
            return value.toLanguageTag()
        }

        override fun fromString(value: String): Locale? {
            return Locale.forLanguageTag(value)
        }
    }
}
