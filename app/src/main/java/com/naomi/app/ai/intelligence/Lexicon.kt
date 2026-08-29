package com.naomi.app.ai.intelligence

/**
 * Shared vocabulary for the local intelligence pipeline.
 *
 * Everything here is domain-neutral on purpose. Naomi must work as well for a
 * gardener as for a kernel developer, so no subject-matter terms belong in this
 * file — only structural words that describe how English sentences are built.
 */
object Lexicon {

    /**
     * Words that carry no topical meaning. Used to break a transcript into
     * candidate keyphrases: a phrase is a run of words containing none of these.
     */
    val stopWords: Set<String> = setOf(
        // articles, determiners
        "a", "an", "the", "this", "that", "these", "those", "some", "any", "each",
        "every", "all", "both", "either", "neither", "another", "such", "no",
        // quantifiers — "uses too much memory" produced a subtopic called
        // "Much Memory" because these words sat inside the candidate phrase
        "much", "many", "few", "fewer", "several", "enough", "little", "plenty",
        "lots", "couple", "bunch",
        // pronouns
        "i", "me", "my", "mine", "myself", "we", "us", "our", "ours", "you", "your",
        "yours", "he", "him", "his", "she", "her", "hers", "it", "its", "they",
        "them", "their", "theirs", "who", "whom", "whose", "which", "what",
        // be / have / do
        "am", "is", "are", "was", "were", "be", "been", "being", "have", "has",
        "had", "having", "do", "does", "did", "doing", "done",
        // modals
        "will", "would", "shall", "should", "can", "could", "may", "might", "must",
        "ought", "let", "lets",
        // prepositions and conjunctions
        "of", "in", "on", "at", "to", "for", "with", "without", "from", "by",
        "about", "into", "onto", "over", "under", "above", "below", "between",
        "through", "during", "before", "after", "since", "until", "till",
        "and", "or", "but", "nor", "so", "yet", "because", "although", "though",
        "while", "whereas", "if", "unless", "than", "as", "up", "down", "out",
        "off", "again", "further", "then", "once", "here", "there", "when",
        "where", "why", "how", "also", "too", "very", "just", "only", "even",
        // discourse filler — extremely common in dictated speech
        "okay", "ok", "well", "like", "actually", "basically", "literally",
        "really", "kind", "sort", "stuff", "thing", "things", "bit", "lot",
        "maybe", "probably", "definitely", "obviously", "anyway", "anyways",
        "um", "uh", "erm", "hmm", "yeah", "yes", "no", "nope", "right", "sure",
        // low-signal verbs
        "get", "gets", "got", "getting", "go", "goes", "going", "went", "gone",
        "make", "makes", "made", "making", "say", "says", "said", "saying",
        "want", "wants", "wanted", "know", "knows", "knew", "think", "thinks",
        "thought", "see", "sees", "saw", "seen", "take", "takes", "took",
        "come", "comes", "came", "put", "puts", "use", "used", "using",
        "now", "today", "still", "back", "way", "time", "day", "days",
        "not", "dont", "doesnt", "didnt", "cant", "wont", "isnt", "arent",
        "wasnt", "werent", "hasnt", "havent", "im", "ive", "ill", "id",
        "youre", "youve", "hes", "shes", "theyre", "theyve", "its", "thats",
        "theres", "heres", "gonna", "wanna", "gotta"
    )

    /**
     * Words that end a keyphrase without being part of one.
     *
     * Plain RAKE breaks phrases only on stop words, which lets a verb sit in the
     * middle of a candidate and produces titles like "Balcony Soil Needs Better"
     * — a truncated clause rather than a thing. Treating verbs and
     * state-adjectives as boundaries leaves the noun phrases either side
     * ("balcony soil", "drainage"), which is what a person would name the note.
     *
     * These are deliberately generic English predicates, not subject-matter terms.
     */
    val phraseBreakers: Set<String> = setOf(
        // being / becoming
        "seem", "seems", "seemed", "look", "looks", "looked", "feel", "feels",
        "felt", "become", "becomes", "became", "stay", "stays", "stayed",
        "remain", "remains", "remained", "turn", "turns", "turned",
        // possession / requirement
        "needs", "need", "needed", "needing", "require", "requires", "required",
        "wants", "lack", "lacks", "lacked", "keep", "keeps", "kept",
        "hold", "holds", "held",
        // common action verbs, in base and inflected form — the base forms were
        // missing at first, which let "seedlings look taller" survive as one
        // candidate and title a memory with a clause.
        "work", "works", "worked", "working", "break", "broke", "breaks", "breaking",
        "fix", "fixed", "fixes", "fixing", "add", "added", "adds", "adding",
        "remove", "removed", "removes", "removing", "start", "started", "starts",
        "starting", "stop", "stopped", "stops", "stopping", "finish", "finished",
        "finishes", "change", "changed", "changes", "move", "moved", "moves",
        "build", "built", "builds", "building", "run", "ran", "runs", "running",
        "find", "found", "finds", "give", "gave", "gives", "tell", "told", "tells",
        "ask", "asked", "asks", "try", "tried", "tries", "trying",
        "check", "checked", "checks", "buy", "bought", "buys",
        "sell", "sold", "sells", "send", "sent", "sends",
        "write", "wrote", "writes", "writing", "read", "reads", "reading",
        "wait", "waits", "waited", "waiting", "drop", "drops", "dropped",
        "uses", "shows", "show", "showed", "means", "mean", "meant",
        "takes", "gets", "goes", "comes", "puts", "sets", "set",
        "repotted", "planted", "watered", "cleaned", "cooked",
        // Comparatives, listed rather than derived. A rule like "ends in -er"
        // would swallow exactly the agent nouns Naomi most needs to keep —
        // buffer, parser, container, header, renderer.
        "better", "worse", "best", "worst", "more", "less", "most", "least",
        "bigger", "smaller", "larger", "taller", "shorter", "longer",
        "higher", "lower", "faster", "slower", "older", "newer",
        "cheaper", "closer", "warmer", "cooler", "stronger", "weaker",
        "harder", "easier", "safer", "wider", "deeper", "heavier", "lighter",
        // state adjectives
        "good", "bad", "fine", "okay", "great", "poor", "done", "ready",
        "broken", "open", "closed", "empty", "full", "new", "old",
        "same", "different", "hard", "easy", "slow", "fast", "big", "small"
    )

    /**
     * When something happened, which is never what it was about.
     *
     * Observed on device: "I need to profile the fence waits tomorrow" created a
     * root topic called "Fence Waits Tomorrow". A date is metadata — the
     * temporal parser already extracts it into a real due date — so letting it
     * into a keyphrase both corrupts the name and duplicates information the
     * memory already carries structurally.
     */
    val temporalWords: Set<String> = setOf(
        "today", "tomorrow", "tonight", "yesterday", "tmrw",
        "morning", "afternoon", "evening", "night", "noon", "midnight",
        "week", "weeks", "weekend", "month", "months", "year", "years",
        "hour", "hours", "minute", "minutes", "second", "seconds",
        "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday",
        "january", "february", "march", "april", "may", "june", "july",
        "august", "september", "october", "november", "december",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec",
        "am", "pm", "oclock", "later", "soon", "earlier", "recently"
    )

    fun isPhraseBreaker(word: String): Boolean {
        val lower = word.lowercase()
        return phraseBreakers.contains(lower) || temporalWords.contains(lower)
    }

    /**
     * Openers that mark the start of an actionable item in speech.
     * Order matters only for readability; matching is by position in the text.
     */
    val taskTriggers: List<String> = listOf(
        "remind me to",
        "i need to",
        "we need to",
        "i have to",
        "we have to",
        "i should",
        "we should",
        "i must",
        "don't forget to",
        "dont forget to",
        "make sure to",
        "make sure i",
        "i'll",
        "i will",
        "we'll",
        "we will",
        "todo",
        "to do"
    )

    /**
     * Leading words stripped from an extracted task title so it reads as an
     * imperative. "should revise the notes" becomes "Revise the notes".
     */
    val taskTitlePrefixes: List<String> = listOf(
        "remind me to", "i need to", "we need to", "i have to", "we have to",
        "don't forget to", "dont forget to", "make sure to", "make sure i",
        "i should", "we should", "i must", "we must",
        "i'll", "i will", "we'll", "we will",
        "should", "need to", "have to", "must", "please", "also", "then",
        "todo", "to do", "go and", "try to", "remember to"
    )

    /**
     * Trailing fragments left behind when a temporal phrase is removed from the
     * end of a task title — "finish the frontend by" becomes "finish the frontend".
     */
    val danglingSuffixes: List<String> = listOf(
        "by", "before", "after", "on", "at", "in", "until", "till", "for",
        "this", "next", "the", "and", "or", "to", "with"
    )

    /**
     * Phrases that signal the speaker has moved to an unrelated subject, used to
     * split one recording into several distinct memories.
     */
    val topicSwitchMarkers: List<String> = listOf(
        "on another note",
        "on a different note",
        "switching gears",
        "changing subject",
        "changing the subject",
        "different topic",
        "unrelated but",
        "separately",
        "also i need to",
        "also, i need to",
        "also remind me to",
        "also don't forget to",
        "also dont forget to",
        "anyway i need to",
        "anyway, i need to",
        "by the way i need to",
        "by the way, i need to"
    )

    /** Openers that introduce a speculative thought worth surfacing as an idea. */
    val ideaMarkers: List<String> = listOf(
        "i think", "i wonder", "maybe", "perhaps", "what if", "it might be",
        "it could be", "an idea", "idea:", "we could", "i could", "possibly"
    )

    /** Openers that introduce a settled conclusion. */
    val decisionMarkers: List<String> = listOf(
        "we decided to", "i decided to", "decided to", "we agreed to",
        "agreed to", "we're going with", "were going with", "going with",
        "the plan is", "we settled on", "final call", "conclusion:"
    )

    /**
     * Conversational openers stripped from the start of a summary so the memory
     * reads as a statement rather than a transcript.
     */
    val summaryOpeners: List<String> = listOf(
        "hey naomi", "ok naomi", "okay naomi", "naomi", "so basically",
        "so", "okay so", "ok so", "well", "um", "uh", "actually", "basically",
        "just a quick note", "quick note", "note to self", "remember that",
        "i wanted to say", "i just wanted to say"
    )

    /** Short tokens that stay lowercase in a title unless they lead it. */
    val titleMinorWords: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "for", "nor", "of", "in", "on",
        "at", "to", "by", "vs", "via", "with", "from", "into", "over"
    )

    fun isStopWord(word: String): Boolean = stopWords.contains(word.lowercase())
}
