package com.naomi.app.domain.usecases

import com.naomi.app.domain.repository.KnowledgeRepository

class ExportMarkdownUseCase(
    private val knowledgeRepository: KnowledgeRepository
) {
    suspend operator fun invoke(rootTopicId: Long? = null): String {
        return knowledgeRepository.exportMarkdown(rootTopicId)
    }
}
