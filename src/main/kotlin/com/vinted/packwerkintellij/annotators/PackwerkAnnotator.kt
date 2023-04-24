package com.vinted.packwerkintellij.annotators

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.markdown.lexer.push
import java.nio.charset.Charset
import java.util.*

internal class PackwerkAnnotator : ExternalAnnotator<PackwerkAnnotator.State, PackwerkAnnotator.Results>() {
    internal class State(var file: PsiFile)
    internal class Results(var problems: List<Problem>)
    internal class Problem(var line: Int, var column: Int, var explanation: String)

    private val violationPattern = Regex("^[^:]+:([0-9]+):([0-9]+)$")

    override fun collectInformation(file: PsiFile): State {
        return State(file)
    }

    override fun doAnnotate(collectedInfo: State): Results {
        // FIXME: think of a better way do do this
        // FIXME: handle unsaved files
        // FIXME: handle virtual (e.g. remote) files
        val relativePath = collectedInfo.file.virtualFile.path
                .removePrefix(collectedInfo.file.project.basePath.toString())
                .removePrefix("/")

        val cmd = GeneralCommandLine("bin/packwerk")
        cmd.withWorkDirectory(collectedInfo.file.project.basePath)

        cmd.charset = Charset.forName("UTF-8")
        cmd.addParameter("check")
        cmd.addParameter(relativePath)

        val process = cmd.createProcess()
        val scanner = Scanner(process.inputStream)
        val problems = ArrayList<Problem>()

        while (scanner.hasNextLine()) {
            val matches = violationPattern.matchEntire(scanner.nextLine())
            if (matches != null) {
                val lineNumber = Integer.valueOf(matches.groupValues[1])
                val columnNumber = Integer.valueOf(matches.groupValues[2])
                val explanation = scanner.nextLine()

                problems.push(Problem(lineNumber, columnNumber, explanation))
            }
        }

        return Results(problems)
    }

    override fun apply(file: PsiFile, annotationResult: Results, holder: AnnotationHolder) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        for (problem in annotationResult.problems) {
            val offset = document.getLineStartOffset(problem.line - 1) + problem.column

            var el = file.findElementAt(offset)
            if (el == null) { el = file }

            holder
                .newAnnotation(HighlightSeverity.ERROR, problem.explanation)
                .range(el.textRange)
                .create()
        }
    }
}
