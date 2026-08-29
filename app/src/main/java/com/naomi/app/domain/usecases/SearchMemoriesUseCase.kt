package com.naomi.app.domain.usecases

import com.naomi.app.domain.model.SearchResult
import com.naomi.app.domain.repository.KnowledgeRepository

class SearchMemoriesUseCase(
    private val knowledgeRepository: KnowledgeRepository
) {
    suspend operator fun invoke(query: String): SearchResult {
        return knowledgeRepository.search(query)
    }
}
