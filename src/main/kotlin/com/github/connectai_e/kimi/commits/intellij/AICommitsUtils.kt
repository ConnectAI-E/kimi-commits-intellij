package com.github.connectai_e.kimi.commits.intellij

import com.github.connectai_e.kimi.commits.intellij.notifications.Notification
import com.github.connectai_e.kimi.commits.intellij.notifications.sendNotification
import com.github.connectai_e.kimi.commits.intellij.settings.AppSettings
import com.github.connectai_e.kimi.commits.intellij.settings.ProjectSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.ModelType
import git4idea.repo.GitRepositoryManager
import java.io.StringWriter
import java.nio.file.FileSystems

object AICommitsUtils {

    fun isPathExcluded(path: String, project: Project): Boolean {
        return !AppSettings.instance.isPathExcluded(path) && !project.service<ProjectSettings>().isPathExcluded(path)
    }

    fun matchesGlobs(text: String, globs: Set<String>): Boolean {
        val fileSystem = FileSystems.getDefault()
        for (globString in globs) {
            val glob = fileSystem.getPathMatcher("glob:$globString")
            if (glob.matches(fileSystem.getPath(text))) {
                return true
            }
        }
        return false
    }

    fun constructPrompt(promptContent: String, diff: String, branch: String, historyMsg: List<String>?): String {
        var content = promptContent
        content = content.replace("{locale}", AppSettings.instance.locale.displayLanguage)
        content = content.replace("{branch}", branch)
        if (content.contains("{history}") and (historyMsg != null))
            content = content.replace("{history}", historyMsg?.joinToString("\n") ?: "")

        return if (content.contains("{diff}")) {
            content.replace("{diff}", diff)
        } else {
            "$content\n$diff"
        }
    }


    fun commonBranch(changes: List<Change>, project: Project): String {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        var branch = changes.map {
            repositoryManager.getRepositoryForFileQuick(it.virtualFile)?.currentBranchName
        }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        if (branch == null) {
            sendNotification(Notification.noCommonBranch())
            // hardcoded fallback branch
            branch = "main"
        }
        return branch
    }

    fun computeDiff(
        includedChanges: List<Change>,
        project: Project
    ): String {

        val gitRepositoryManager = GitRepositoryManager.getInstance(project)

        // go through included changes, create a map of repository to changes and discard nulls
        val changesByRepository = includedChanges
            .filter {
                it.virtualFile?.path?.let { path ->
                    AICommitsUtils.isPathExcluded(path, project)
                } ?: false
            }
            .mapNotNull { change ->
                change.virtualFile?.let { file ->
                    gitRepositoryManager.getRepositoryForFileQuick(
                        file
                    ) to change
                }
            }
            .groupBy({ it.first }, { it.second })


        // compute diff for each repository
        return changesByRepository
            .map { (repository, changes) ->
                repository?.let {
                    val filePatches = IdeaTextPatchBuilder.buildPatch(
                        project,
                        changes,
                        repository.root.toNioPath(), false, true
                    )

                    val stringWriter = StringWriter()
                    stringWriter.write("Repository: ${repository.root.path}\n")
                    UnifiedDiffWriter.write(project, filePatches, stringWriter, "\n", null)
                    stringWriter.toString()
                }
            }
            .joinToString("\n")
    }

    fun computeGitHistoryMsg(project: Project,number: Number=10): List<String> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repository = repositoryManager.repositories.firstOrNull() ?: return emptyList()
        var filePath = VcsUtil.getFilePath(repository.root)
        val vcsHistoryProvider = ProjectLevelVcsManager.getInstance(project).getVcsFor(filePath)
            ?.getVcsHistoryProvider() ?: return emptyList()
        val vcsHistorySession = vcsHistoryProvider.createSessionFor(filePath)
        val vcsHistory = vcsHistorySession?.getRevisionList() ?: return emptyList()
        val vcsHistoryList = vcsHistory.take(number.toInt())

        val historyList = mutableListOf<String>()
        vcsHistoryList.forEach {
            historyList.add(it.commitMessage?.trim() ?: "")
        }
        return historyList


    }

    fun isPromptTooLarge(prompt: String): Boolean {
        val registry = Encodings.newDefaultEncodingRegistry()

        /*
         * Try to find the model type based on the model id by finding the longest matching model type
         * If no model type matches, let the request go through and let the KimiAI API handle it
         */
        val modelType = ModelType.entries
            .filter { AppSettings.instance.openAIModelId.contains(it.name) }
            .maxByOrNull { it.name.length }
            ?: return false

        val encoding = registry.getEncoding(modelType.encodingType)
        return encoding.countTokens(prompt) > modelType.maxContextLength
    }
}
