package com.dataops.basecamp.domain.command.github

import com.dataops.basecamp.common.enums.PullRequestState

/**
 * Pull Request 필터 조건
 */
data class PullRequestFilter(
    val state: PullRequestState? = null,
    val author: String? = null,
    val targetBranch: String? = null,
    val limit: Int = 30,
)
