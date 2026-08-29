package com.naomi.app.presentation.hierarchy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.domain.model.TopicNode
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicHierarchyScreen(
    viewModel: TopicHierarchyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTopicDetail: (Long) -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    val treeState by viewModel.treeState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadTopicTree()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Knowledge Tree",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val centered = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = NaomiSpacing.lg)

        when (val state = treeState) {
            // Nothing is drawn while loading. The tree resolves in a few
            // milliseconds from a local database, so a spinner would only
            // flash — but claiming "Nothing organized yet." during that frame,
            // as the screen used to, is worse than showing nothing at all.
            is TopicTreeUiState.Loading -> Box(centered) {}

            is TopicTreeUiState.Failed -> Box(centered, contentAlignment = Alignment.Center) {
                NaomiEmptyState(title = "Couldn't load your topics", message = state.message)
            }

            is TopicTreeUiState.Loaded -> {
                if (state.roots.isEmpty()) {
                    Box(centered, contentAlignment = Alignment.Center) {
                        NaomiEmptyState(
                            title = "Nothing organized yet",
                            message = "Speak a few memories and Naomi will build this tree from what you talk about."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = centered,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(NaomiSpacing.xs))
                            NaomiSectionHeader(title = "HIERARCHY")
                            Spacer(modifier = Modifier.height(NaomiSpacing.xs))
                        }

                        items(state.roots, key = { it.topic.id }) { rootNode ->
                            TopicTreeNodeView(
                                node = rootNode,
                                onTopicClick = { onNavigateToTopicDetail(it) },
                                onNoteClick = { onNavigateToNoteDetail(it) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(NaomiSpacing.xl))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopicTreeNodeView(
    node: TopicNode,
    onTopicClick: (Long) -> Unit,
    onNoteClick: (Long) -> Unit,
    level: Int = 0
) {
    // Keyed on the topic so a rotation or process death restores the branch the
    // user actually collapsed, rather than resetting the whole tree to open.
    var expanded by rememberSaveable(node.topic.id) { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 20).dp)
    ) {
        // A root topic used to be boxed and its children left bare, so depth was
        // signalled twice — once by indentation, once by a border that only the
        // top level had. Indentation and type weight already say it; the box was
        // saying it louder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTopicClick(node.topic.id) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NaomiSpacing.md - 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Column {
                        Text(
                            text = node.topic.name,
                            style = if (level == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (level == 0) FontWeight.Medium else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (node.noteCount > 0) {
                            Text(
                                text = "${node.noteCount} ${if (node.noteCount == 1) "memory" else "memories"}",
                                style = NaomiMonoLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (node.subtopics.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (childNode in node.subtopics) {
                    TopicTreeNodeView(
                        node = childNode,
                        onTopicClick = onTopicClick,
                        onNoteClick = onNoteClick,
                        level = level + 1
                    )
                }
            }
        }
    }
}

