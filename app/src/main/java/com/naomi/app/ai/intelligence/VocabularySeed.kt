package com.naomi.app.ai.intelligence

import com.naomi.app.data.database.entities.VocabularyEntity
import java.util.Locale

/**
 * The words Naomi knows before the user has said anything.
 *
 * A vocabulary learned purely from the user's own memories is empty on the day
 * it is needed most — the first capture. This list closes that gap for the terms
 * a speech recogniser is most likely to mangle: product names with unusual
 * spellings, and technical words that are not in a general English model.
 *
 * ## Why a generic list is safe here
 *
 * Seeding carries an obvious risk: "Kotlin" and "cotton" reduce to sound-keys
 * one character apart, so a naive matcher would rewrite a sentence about
 * bedsheets. It does not happen, because seeded terms start at zero occurrences
 * and belong to no topic, so they can earn no contextual evidence at all. Under
 * [TranscriptNormalizer]'s scoring, sounding alike is not enough on its own —
 * a seeded term can only win when the recogniser *itself* offered it as an
 * alternative hypothesis, which is the recogniser agreeing, not Naomi guessing.
 *
 * Terms the user actually says are reinforced and become topics, at which point
 * they gain the context that lets them correct on their own.
 *
 * Deliberately excluded: ordinary English words that happen to be product names
 * ("Go", "Rust", "Swift", "React"). Correcting them changes only capitalisation,
 * which is not worth the risk of touching a common word at all.
 */
object VocabularySeed {

    private val LANGUAGES_AND_RUNTIMES = listOf(
        "Kotlin", "Java", "JavaScript", "TypeScript", "Python", "Ruby", "Scala",
        "Haskell", "Erlang", "Elixir", "Clojure", "Perl", "PHP", "Lua",
        "Node.js", "Deno", "GraalVM", "LLVM", "WebAssembly", "CUDA", "OpenCL"
    )

    private val PLATFORMS_AND_FRAMEWORKS = listOf(
        "Android", "iOS", "Jetpack Compose", "SwiftUI", "Flutter", "Xamarin",
        "Angular", "Vue", "Svelte", "Next.js", "Django", "Flask", "FastAPI",
        "Spring Boot", "Rails", "Laravel", "Express", "Gradle", "Maven",
        "Bazel", "Webpack", "Vite", "ESLint", "Room", "Retrofit", "Dagger",
        "Hilt", "Coroutines", "RxJava", "Ktor", "Espresso", "Robolectric"
    )

    private val DATA_AND_INFRA = listOf(
        "PostgreSQL", "MySQL", "SQLite", "MongoDB", "Redis", "Cassandra",
        "DynamoDB", "Elasticsearch", "Kafka", "RabbitMQ", "Nginx", "Apache",
        "Kubernetes", "Docker", "Terraform", "Ansible", "Helm", "Prometheus",
        "Grafana", "Jenkins", "GitLab", "GitHub", "Bitbucket", "Firebase",
        "Supabase", "Cloudflare", "DigitalOcean", "Kibana", "Airflow"
    )

    private val CONCEPTS = listOf(
        "API", "REST", "GraphQL", "gRPC", "WebSocket", "OAuth", "JWT", "SAML",
        "CI/CD", "DevOps", "Kubernetes cluster", "microservice", "middleware",
        "webhook", "idempotent", "mutex", "semaphore", "deadlock", "race condition",
        "ring buffer", "garbage collection", "memory leak", "stack trace",
        "regression", "refactor", "linter", "changelog", "monorepo", "rollback",
        "feature flag", "canary", "throughput", "latency", "backpressure"
    )

    private val HARDWARE_AND_GRAPHICS = listOf(
        "GPU", "CPU", "TPU", "NPU", "SoC", "FPGA", "Vulkan", "OpenGL", "DirectX",
        "Metal", "shader", "framebuffer", "rasteriser", "viewport", "mipmap",
        "GDDR", "DRAM", "SRAM", "cache line", "firmware", "kernel", "driver"
    )

    private val TOOLS = listOf(
        "Figma", "Slack", "Notion", "Jira", "Confluence", "Trello", "Asana",
        "Postman", "Datadog", "Sentry", "Splunk", "Tableau", "Zoom", "Miro"
    )

    private val AI = listOf(
        "Gemini", "Claude", "ChatGPT", "OpenAI", "Anthropic", "TensorFlow",
        "PyTorch", "Keras", "Hugging Face", "LangChain", "embedding",
        "transformer", "tokeniser", "inference", "fine-tuning", "quantisation"
    )

    /**
     * Every seeded term as a row ready to insert.
     *
     * Occurrences stay at zero on purpose — see the class note. `lastSeenAt` is
     * passed in so a test can pin it and so every row in one seeding run agrees.
     */
    fun entities(seededAt: Long): List<VocabularyEntity> {
        fun rows(terms: List<String>, kind: String) = terms.map { term ->
            VocabularyEntity(
                term = term,
                normalized = term.lowercase(Locale.ROOT),
                phoneticKey = Phonetics.key(term),
                kind = kind,
                source = VocabularyEntity.SOURCE_SEEDED,
                occurrences = 0,
                lastSeenAt = seededAt
            )
        }

        return (
            rows(LANGUAGES_AND_RUNTIMES, VocabularyEntity.KIND_TECH) +
                rows(PLATFORMS_AND_FRAMEWORKS, VocabularyEntity.KIND_TECH) +
                rows(DATA_AND_INFRA, VocabularyEntity.KIND_TECH) +
                rows(CONCEPTS, VocabularyEntity.KIND_TERM) +
                rows(HARDWARE_AND_GRAPHICS, VocabularyEntity.KIND_TERM) +
                rows(TOOLS, VocabularyEntity.KIND_TECH) +
                rows(AI, VocabularyEntity.KIND_TECH)
            ).distinctBy { it.normalized }
    }
}
