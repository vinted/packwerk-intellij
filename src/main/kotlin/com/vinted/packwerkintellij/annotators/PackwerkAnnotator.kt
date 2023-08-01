package com.vinted.packwerkintellij.annotators

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.vinted.packwerkintellij.PackwerkSettingsState
import org.intellij.markdown.lexer.push
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RColonReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RConstant
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit

private val violationPattern = Regex("^[^:]+:([0-9]+):([0-9]+)$")

private const val runTimeout: Long = 30
private val runTimeoutUnit = TimeUnit.SECONDS

internal class PackwerkAnnotator : ExternalAnnotator<PackwerkAnnotator.State, PackwerkAnnotator.Results>() {
    internal class State(
        var file: PsiFile,
        var packwerkPath: String,
        var fileText: String?,
        var ignoreRecordedViolations: Boolean,
    )

    internal class Results(var problems: List<Problem>)
    internal class Problem(var line: Int, var column: Int, var explanation: String)

    override fun collectInformation(file: PsiFile): State? {
        val settings = file.project.service<PackwerkSettingsState>()
        if (!settings.enabled) {
            return null
        }

        if (!settings.lintUnsavedFiles && FileDocumentManager.getInstance().isFileModified(file.virtualFile)) {
            thisLogger().debug("Not linting because the file is modified")
            return null
        }

        val root = getRootForFile(file)
        if (root == null) {
            thisLogger().debug("Not linting because project root could not be determined")
            return null
        }

        val fileText = if (settings.lintUnsavedFiles) {
            file.text
        } else {
            null
        }

        return State(file, settings.packwerkPath, fileText, settings.ignoreRecordedViolations)
    }

    override fun doAnnotate(collectedInfo: State): Results? {
        val root: VirtualFile = getRootForFile(collectedInfo.file) ?: return null
        val relativePath = VfsUtilCore.getRelativePath(collectedInfo.file.virtualFile, root) ?: return null

        val cmdParams = ArrayList<String>()

        val useStdin = collectedInfo.fileText != null
        if (useStdin) {
            cmdParams.push("check-contents")
        } else {
            cmdParams.push("check")
        }

        if (collectedInfo.ignoreRecordedViolations) {
            cmdParams.push("--ignore-recorded-violations")
        }

        cmdParams.push("--")
        cmdParams.push(relativePath)

        val cmd = GeneralCommandLine(collectedInfo.packwerkPath)
            .withWorkDirectory(root.path)
            .withCharset(Charset.forName("UTF-8"))
            .withParameters(cmdParams)

        val process: Process
        try {
            process = cmd.createProcess()
        } catch (e: ExecutionException) {
            thisLogger().debug("Not linting because Packwerk could not be executed", e)
            return null
        }

        if (useStdin) {
            process.outputStream.write(collectedInfo.fileText!!.toByteArray())
            process.outputStream.flush()
            process.outputStream.close()
        }

        if (!process.waitFor(runTimeout, runTimeoutUnit)) {
            process.destroyForcibly().waitFor()
            thisLogger().warn("Packwerk process timed out")
            return null
        }

        val scanner = Scanner(process.inputStream)
        val problems = ArrayList<Problem>()

        while (scanner.hasNextLine()) {
            val matches = violationPattern.matchEntire(scanner.nextLine())
            if (matches != null) {
                val lineNumber = (Integer.valueOf(matches.groupValues[1]) - 1).coerceAtLeast(0)
                val columnNumber = (Integer.valueOf(matches.groupValues[2]) - 1).coerceAtLeast(0)
                val explanation = scanner.nextLine()

                problems.push(Problem(lineNumber, columnNumber, explanation))
            }
        }

        return Results(problems)
    }

    override fun apply(file: PsiFile, annotationResult: Results, holder: AnnotationHolder) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        for (problem in annotationResult.problems) {
            if (problem.line < 0 || problem.line >= document.lineCount) {
                continue
            }

            val lineEndOffset = document.getLineEndOffset(problem.line)
            val offset = document.getLineStartOffset(problem.line) + problem.column + 1

            if (offset >= lineEndOffset) {
                continue
            }

            var el = file.findElementAt(offset)

            // Expand compound constants (e.g. Foo::Bar)
            while (el != null && (el.parent is RConstant || el.parent is RColonReference)) {
                el = el.parent
            }

            if (el == null) {
                el = file
            }

            holder
                .newAnnotation(HighlightSeverity.ERROR, problem.explanation)
                .range(el.textRange)
                .create()
        }
    }

    private fun getRootForFile(file: PsiFile): VirtualFile? {
        val application = ApplicationManager.getApplication()
        var root: VirtualFile? = null

        application.runReadAction {
            root = ProjectFileIndex
                .getInstance(file.project)
                .getContentRootForFile(file.virtualFile)
        }

        return root
    }
}
