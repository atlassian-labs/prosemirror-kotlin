package com.atlassian.prosemirror.model

// import java.util.regex.Pattern

// import {Fragment} from "./fragment"
// import {NodeType} from "./schema"

// type MatchEdge = {type: NodeType, next: ContentMatch}

data class MatchEdge(val type: NodeType, val next: ContentMatch)

// /// Instances of this class represent a match state of a node type's
// /// [content expression](#model.NodeSpec.content), and can be used to
// /// find out whether further content matches here, and whether a given
// /// position is a valid end of the node.

// region ContentMatch
// export class ContentMatch {
//     /// @internal
//     readonly next: MatchEdge[] = []
//     /// @internal
//     readonly wrapCache: (NodeType | readonly NodeType[] | null)[] = []
//
//     /// @internal
//     constructor(
//         /// True when this match state represents a valid end of the node.
//         readonly validEnd: boolean
//     ) {}
//
//     /// @internal
//     static parse(string: string, nodeTypes: {readonly [name: string]: NodeType}): ContentMatch {
//         let stream = new TokenStream(string, nodeTypes)
//         if (stream.next == null) return ContentMatch.empty
//         let expr = parseExpr(stream)
//         if (stream.next) stream.err("Unexpected trailing text")
//         let match = dfa(nfa(expr))
//         checkForDeadEnds(match, stream)
//         return match
//     }
//
//     /// Match a node type, returning a match after that node if
//     /// successful.
//     matchType(type: NodeType): ContentMatch | null {
//         for (let i = 0; i < this.next.length; i++)
//         if (this.next[i].type == type) return this.next[i].next
//         return null
//     }
//
//     /// Try to match a fragment. Returns the resulting match when
//     /// successful.
//     matchFragment(frag: Fragment, start = 0, end = frag.childCount): ContentMatch | null {
//         let cur: ContentMatch | null = this
//         for (let i = start; cur && i < end; i++)
//         cur = cur.matchType(frag.child(i).type)
//         return cur
//     }
//
//     /// @internal
//     get inlineContent() {
//         return this.next.length && this.next[0].type.isInline
//     }
//
//     /// Get the first matching node type at this match position that can
//     /// be generated.
//     get defaultType(): NodeType | null {
//         for (let i = 0; i < this.next.length; i++) {
//             let {type} = this.next[i]
//             if (!(type.isText || type.hasRequiredAttrs())) return type
//         }
//         return null
//     }
//
//     /// @internal
//     compatible(other: ContentMatch) {
//         for (let i = 0; i < this.next.length; i++)
//         for (let j = 0; j < other.next.length; j++)
//         if (this.next[i].type == other.next[j].type) return true
//         return false
//     }
//
//     /// Try to match the given fragment, and if that fails, see if it can
//     /// be made to match by inserting nodes in front of it. When
//     /// successful, return a fragment of inserted nodes (which may be
//     /// empty if nothing had to be inserted). When `toEnd` is true, only
//     /// return a fragment if the resulting match goes to the end of the
//     /// content expression.
//     fillBefore(after: Fragment, toEnd = false, startIndex = 0): Fragment | null {
//         let seen: ContentMatch[] = [this]
//         function search(match: ContentMatch, types: readonly NodeType[]): Fragment | null {
//             let finished = match.matchFragment(after, startIndex)
//             if (finished && (!toEnd || finished.validEnd))
//                 return Fragment.from(types.map(tp => tp.createAndFill()!))
//
//             for (let i = 0; i < match.next.length; i++) {
//             let {type, next} = match.next[i]
//             if (!(type.isText || type.hasRequiredAttrs()) && seen.indexOf(next) == -1) {
//                 seen.push(next)
//                 let found = search(next, types.concat(type))
//                 if (found) return found
//             }
//         }
//             return null
//         }
//
//         return search(this, [])
//     }
//
//     /// Find a set of wrapping node types that would allow a node of the
//     /// given type to appear at this position. The result may be empty
//     /// (when it fits directly) and will be null when no such wrapping
//     /// exists.
//     findWrapping(target: NodeType): readonly NodeType[] | null {
//         for (let i = 0; i < this.wrapCache.length; i += 2)
//         if (this.wrapCache[i] == target) return this.wrapCache[i + 1] as (readonly NodeType[] | null)
//         let computed = this.computeWrapping(target)
//         this.wrapCache.push(target, computed)
//         return computed
//     }
//
//     /// @internal
//     computeWrapping(target: NodeType): readonly NodeType[] | null {
//         type Active = {match: ContentMatch, type: NodeType | null, via: Active | null}
//         let seen = Object.create(null), active: Active[] = [{match: this, type: null, via: null}]
//         while (active.length) {
//             let current = active.shift()!, match = current.match
//             if (match.matchType(target)) {
//                 let result: NodeType[] = []
//                 for (let obj: Active = current; obj.type; obj = obj.via!)
//                 result.push(obj.type)
//                 return result.reverse()
//             }
//             for (let i = 0; i < match.next.length; i++) {
//                 let {type, next} = match.next[i]
//                 if (!type.isLeaf && !type.hasRequiredAttrs() && !(type.name in seen) &&
//                      (!current.type || next.validEnd)) {
//                     active.push({match: type.contentMatch, type, via: current})
//                     seen[type.name] = true
//                 }
//             }
//         }
//         return null
//     }
//
//     /// The number of outgoing edges this node has in the finite
//     /// automaton that describes the content expression.
//     get edgeCount() {
//         return this.next.length
//     }
//
//     /// Get the _n_​th outgoing edge from this node in the finite
//     /// automaton that describes the content expression.
//     edge(n: number): MatchEdge {
//         if (n >= this.next.length) throw new RangeError(`There's no ${n}th edge in this content match`)
//         return this.next[n]
//     }
//
//     /// @internal
//     toString() {
//         let seen: ContentMatch[] = []
//         function scan(m: ContentMatch) {
//             seen.push(m)
//             for (let i = 0; i < m.next.length; i++)
//             if (seen.indexOf(m.next[i].next) == -1) scan(m.next[i].next)
//         }
//         scan(this)
//         return seen.map((m, i) => {
//             let out = i + (m.validEnd ? "*" : " ") + " "
//             for (let i = 0; i < m.next.length; i++)
//             out += (i ? ", " : "") + m.next[i].type.name + "->" + seen.indexOf(m.next[i].next)
//             return out
//         }).join("\n")
//     }
//
//     /// @internal
//     static empty = new ContentMatch(true)
// }
// endregion
class ContentMatch internal constructor(
    // True when this match state represents a valid end of the node.
    val validEnd: Boolean
) {
    internal val next: MutableList<MatchEdge> = mutableListOf()
    internal val wrapCache: MutableList<Pair<NodeType, List<NodeType>?>> = mutableListOf()

    internal val inlineContent: Boolean
        get() = this.next.firstOrNull()?.type?.isInline == true

    // Get the first matching node type at this match position that can be generated.
    val defaultType: NodeType?
        get() {
            return next.firstOrNull { match ->
                val type = match.type
                !(type.isText || type.hasRequiredAttrs())
            }?.type
        }

    // Match a node type, returning a match after that node if successful.
    fun matchType(type: NodeType): ContentMatch? {
        return next.firstOrNull {
            it.type == type
        }?.next
    }

    // Try to match a fragment. Returns the resulting match when successful.
    fun matchFragment(frag: Fragment, start: Int = 0, end: Int = frag.childCount): ContentMatch? {
        var cur: ContentMatch? = this
        for (i in start until end) {
            cur = cur?.matchType(frag.child(i).type)
            if (cur == null) break
        }
        return cur
    }

    internal fun compatible(other: ContentMatch): Boolean {
        this.next.forEach { thisMatch ->
            other.next.forEach { otherMatch ->
                if (thisMatch.type == otherMatch.type) return true
            }
        }
        return false
    }

    // Try to match the given fragment, and if that fails, see if it can be made to match by
    // inserting nodes in front of it. When successful, return a fragment of inserted nodes (which
    // may be empty if nothing had to be inserted). When `toEnd` is true, only return a fragment if
    // the resulting match goes to the end of the content expression.
    fun fillBefore(after: Fragment, toEnd: Boolean = false, startIndex: Int = 0): Fragment? {
        val seen = mutableListOf(this)
        var search: ((match: ContentMatch, types: List<NodeType>) -> Fragment?)? = null
        search = fun (match: ContentMatch, types: List<NodeType>): Fragment? {
            val finished = match.matchFragment(after, startIndex)
            if (finished != null && (!toEnd || finished.validEnd)) {
                return Fragment.Companion.from(types.mapNotNull { tp -> tp.createAndFill(null, null as Node?, null) })
            }

            for (nextMatch in match.next) {
                val type = nextMatch.type
                val next = nextMatch.next
                if (!(type.isText || type.hasRequiredAttrs()) && seen.indexOf(next) == -1) {
                    seen.add(next)
                    val found = search?.invoke(next, types + type)
                    if (found != null) return found
                }
            }
            return null
        }

        return search(this, emptyList())
    }

    // Find a set of wrapping node types that would allow a node of the given type to appear at this
    // position. The result may be empty (when it fits directly) and will be null when no such
    // wrapping exists.
    fun findWrapping(target: NodeType): List<NodeType>? {
        for (pair in wrapCache) {
            if (pair.first == target) {
                return pair.second
            }
        }
        val computed = this.computeWrapping(target)
        wrapCache.add(target to computed)
        return computed
    }

    @Suppress("ComplexCondition")
    internal fun computeWrapping(target: NodeType): List<NodeType>? {
        data class Active(val match: ContentMatch, val type: NodeType?, val via: Active?)
        val seen = mutableMapOf<String, Boolean>()
        val active = mutableListOf(Active(this, null, null))
        while (active.isNotEmpty()) {
            val current = active.removeFirst()
            val match = current.match
            if (match.matchType(target) != null) {
                val result = mutableListOf<NodeType>()
                var obj: Active = current
                while (obj.type != null) {
                    result.add(obj.type!!)
                    obj = obj.via!!
                }
                result.reverse()
                return result.toList()
            }
            for (matchEdge in match.next) {
                val type = matchEdge.type
                val next = matchEdge.next
                if (
                    !type.isLeaf &&
                    !type.hasRequiredAttrs() &&
                    type.name !in seen &&
                    (current.type == null || next.validEnd)
                ) {
                    active.add(Active(match = type.contentMatch, type, via = current))
                    seen[type.name] = true
                }
            }
        }
        return null
    }

    // The number of outgoing edges this node has in the finite automaton that describes the content
    // expression.
    val edgeCount: Int
        get() = this.next.size

    // Get the _n_th outgoing edge from this node in the finite automaton that describes the content
    // expression.
    fun edge(n: Int): MatchEdge {
        if (n >= this.next.size) throw RangeError("There's no ${n}th edge in this content match")
        return this.next[n]
    }

    override fun toString(): String {
        val seen = mutableListOf<ContentMatch>()
        var scan: ((ContentMatch) -> Unit)? = null
        scan = fun(m: ContentMatch) {
            seen.add(m)
            m.next.forEach { edge ->
                if (!seen.contains(edge.next)) scan?.invoke(edge.next)
            }
        }
        scan(this)
        return seen.mapIndexed { i, m ->
            var out = i.toString() + (if (m.validEnd) "*" else " ") + " "
            m.next.forEachIndexed { index, matchEdge ->
                out += (if (index != 0) ", " else "") + matchEdge.type.name + "->" + seen.indexOf(matchEdge.next)
            }
            return out
        }.joinToString("\n")
    }

    companion object {
        val empty = ContentMatch(true)

        internal fun parse(string: String, nodeTypes: Map<String, NodeType>): ContentMatch {
            val stream = TokenStream(string, nodeTypes)
            if (stream.next.isNullOrEmpty()) return empty
            val expr = parseExpr(stream)
            if (!stream.next.isNullOrEmpty()) stream.err("Unexpected trailing text")
            val match = dfa(nfa(expr))
            checkForDeadEnds(match, stream)
            return match
        }
    }
}

// region TokenStream
// class TokenStream {
//     inline: boolean | null = null
//     pos = 0
//     tokens: string[]
//
//     constructor(
//         readonly string: string,
//         readonly nodeTypes: {readonly [name: string]: NodeType}
//     ) {
//         this.tokens = string.split(/\s*(?=\b|\W|$)/)
//         if (this.tokens[this.tokens.length - 1] == "") this.tokens.pop()
//         if (this.tokens[0] == "") this.tokens.shift()
//     }
//
//     get next() { return this.tokens[this.pos] }
//
//     eat(tok: string) { return this.next == tok && (this.pos++ || true) }
//
//     err(str: string): never { throw new SyntaxError(str + " (in content expression '" + this.string + "')") }
// }
// endregion
class TokenStream(
    val string: String,
    val nodeTypes: Map<String, NodeType>
) {
    var inline: Boolean? = null
    var pos = 0
    val tokens: MutableList<String> =
        string.split("""\s*(?=\b|\W|$)""".toRegex()).filter { it.isNotEmpty() }.toMutableList()

    val next: String?
        get() = this.tokens.getOrNull(this.pos)

    fun eat(tok: String): Boolean {
        return (next == tok).apply { if (this) pos++ }
    }

    fun err(str: String) {
        throw IllegalStateException("SyntaxError: $str (in content expression '$string')")
    }
}

// type Expr =
// {type: "choice", exprs: Expr[]} |
// {type: "seq", exprs: Expr[]} |
// {type: "plus", expr: Expr} |
// {type: "star", expr: Expr} |
// {type: "opt", expr: Expr} |
// {type: "range", min: number, max: number, expr: Expr} |
// {type: "name", value: NodeType}
sealed interface Expr {
    data class Choice(val exprs: List<Expr>) : Expr
    data class Seq(val exprs: List<Expr>) : Expr
    data class Plus(val expr: Expr) : Expr
    data class Star(val expr: Expr) : Expr
    data class Opt(val expr: Expr) : Expr
    data class Range(val min: Int, val max: Int, val expr: Expr) : Expr
    data class Name(val value: NodeType) : Expr
}

// function parseExpr(stream: TokenStream): Expr {
//     let exprs = []
//     do { exprs.push(parseExprSeq(stream)) }
//     while (stream.eat("|"))
//     return exprs.length == 1 ? exprs[0] : {type: "choice", exprs}
// }
fun parseExpr(stream: TokenStream): Expr {
    val exprs = mutableListOf<Expr>()
    do { exprs.add(parseExprSeq(stream)) }
    while (stream.eat("|"))
    return if (exprs.size == 1) exprs[0] else Expr.Choice(exprs)
}

// function parseExprSeq(stream: TokenStream): Expr {
//     let exprs = []
//     do { exprs.push(parseExprSubscript(stream)) }
//     while (stream.next && stream.next != ")" && stream.next != "|")
//     return exprs.length == 1 ? exprs[0] : {type: "seq", exprs}
// }
fun parseExprSeq(stream: TokenStream): Expr {
    val exprs = mutableListOf<Expr>()
    do {
        exprs.add(parseExprSubscript(stream))
    } while (!stream.next.isNullOrEmpty() && stream.next != ")" && stream.next != "|")
    return if (exprs.size == 1) exprs[0] else Expr.Seq(exprs)
}

// function parseExprSubscript(stream: TokenStream): Expr {
//     let expr = parseExprAtom(stream)
//     for (;;) {
//         if (stream.eat("+"))
//             expr = {type: "plus", expr}
//         else if (stream.eat("*"))
//             expr = {type: "star", expr}
//         else if (stream.eat("?"))
//             expr = {type: "opt", expr}
//         else if (stream.eat("{"))
//             expr = parseExprRange(stream, expr)
//         else break
//     }
//     return expr
// }
fun parseExprSubscript(stream: TokenStream): Expr {
    var expr = parseExprAtom(stream)
    while (true) {
        expr = if (stream.eat("+")) {
          Expr.Plus(expr)
        } else if (stream.eat("*")) {
          Expr.Star(expr)
        } else if (stream.eat("?")) {
          Expr.Opt(expr)
        } else if (stream.eat("{")) {
            parseExprRange(stream, expr)
        } else {
            break
        }
    }
    return expr
}

// function parseNum(stream: TokenStream) {
//     if (/\D/.test(stream.next)) stream.err("Expected number, got '" + stream.next + "'")
//     let result = Number(stream.next)
//     stream.pos++
//     return result
// }
fun parseNum(stream: TokenStream): Int {
    val digitRegex = "\\D".toRegex()
    if (digitRegex.matches(stream.next!!)) {
        stream.err("Expected number, got '${stream.next}'")
    }
    val result = stream.next!!.toInt()
    stream.pos++
    return result
}

// function parseExprRange(stream: TokenStream, expr: Expr): Expr {
//     let min = parseNum(stream), max = min
//     if (stream.eat(",")) {
//         if (stream.next != "}") max = parseNum(stream)
//         else max = -1
//     }
//     if (!stream.eat("}")) stream.err("Unclosed braced range")
//     return {type: "range", min, max, expr}
// }
fun parseExprRange(stream: TokenStream, expr: Expr): Expr.Range {
    val min = parseNum(stream)
    var max = min
    if (stream.eat(",")) {
        max = if (stream.next != "}") parseNum(stream) else -1
    }
    if (!stream.eat("}")) stream.err("Unclosed braced range")
    return Expr.Range(min, max, expr)
}

// function resolveName(stream: TokenStream, name: string): readonly NodeType[] {
//     let types = stream.nodeTypes, type = types[name]
//     if (type) return [type]
//     let result = []
//     for (let typeName in types) {
//         let type = types[typeName]
//         if (type.groups.indexOf(name) > -1) result.push(type)
//     }
//     if (result.length == 0) stream.err("No node type or group '" + name + "' found")
//     return result
// }
fun resolveName(stream: TokenStream, name: String): List<NodeType> {
    val types = stream.nodeTypes
    val type = types[name]
    if (type != null) return listOf(type)
    val result = mutableListOf<NodeType>()
    for (typeName in types.keys) {
        types[typeName]?.let {
            if (it.groups.indexOf(name) > -1) {
                result.add(it)
            }
        }
    }
    return result
}

// function parseExprAtom(stream: TokenStream): Expr {
//     if (stream.eat("(")) {
//         let expr = parseExpr(stream)
//         if (!stream.eat(")")) stream.err("Missing closing paren")
//         return expr
//     } else if (!/\W/.test(stream.next)) {
//         let exprs = resolveName(stream, stream.next).map(type => {
//         if (stream.inline == null) stream.inline = type.isInline
//         else if (stream.inline != type.isInline) stream.err("Mixing inline and block content")
//         return {type: "name", value: type} as Expr
//     })
//         stream.pos++
//         return exprs.length == 1 ? exprs[0] : {type: "choice", exprs}
//     } else {
//         stream.err("Unexpected token '" + stream.next + "'")
//     }
// }
fun parseExprAtom(stream: TokenStream): Expr {
    val wordRegex = "\\W".toRegex()
    if (stream.eat("(")) {
        val expr = parseExpr(stream)
        if (!stream.eat(")")) stream.err("Missing closing paren")
        return expr
    } else if (!wordRegex.matches(stream.next!!)) {
        val exprs = resolveName(stream, stream.next!!).map { type ->
            if (stream.inline == null) {
                stream.inline = type.isInline
            } else if (stream.inline != type.isInline) {
                stream.err("Mixing inline and block content for ${type.name}")
            }
            return@map Expr.Name(type)
        }
        stream.pos++
        return if (exprs.size == 1) exprs[0] else Expr.Choice(exprs)
    } else {
        stream.err("Unexpected token '${stream.next}'")
        throw IllegalStateException("No way we get this exception")
    }
}

// The code below helps compile a regular-expression-like language into a deterministic finite
// automaton. For a good introduction to these concepts, see
// https://swtch.com/~rsc/regexp/regexp1.html
//
// type Edge = {term: NodeType | undefined, to: number | undefined}
class Edge(val term: NodeType?, var to: Int?)

// region NFA
// /// Construct an NFA from an expression as returned by the parser. The
// /// NFA is represented as an array of states, which are themselves
// /// arrays of edges, which are `{term, to}` objects. The first state is
// /// the entry state and the last node is the success state.
// ///
// /// Note that unlike typical NFAs, the edge ordering in this one is
// /// significant, in that it is used to contruct filler content when
// /// necessary.
// function nfa(expr: Expr): Edge[][] {
//     let nfa: Edge[][] = [[]]
//     connect(compile(expr, 0), node())
//     return nfa
//
//     function node() { return nfa.push([]) - 1 }
//     function edge(from: number, to?: number, term?: NodeType) {
//         let edge = {term, to}
//         nfa[from].push(edge)
//         return edge
//     }
//     function connect(edges: Edge[], to: number) {
//         edges.forEach(edge => edge.to = to)
//     }
//
//     function compile(expr: Expr, from: number): Edge[] {
//         if (expr.type == "choice") {
//             return expr.exprs.reduce((out, expr) => out.concat(compile(expr, from)), [] as Edge[])
//         } else if (expr.type == "seq") {
//             for (let i = 0;; i++) {
//                 let next = compile(expr.exprs[i], from)
//                 if (i == expr.exprs.length - 1) return next
//                 connect(next, from = node())
//             }
//         } else if (expr.type == "star") {
//             let loop = node()
//             edge(from, loop)
//             connect(compile(expr.expr, loop), loop)
//             return [edge(loop)]
//         } else if (expr.type == "plus") {
//             let loop = node()
//             connect(compile(expr.expr, from), loop)
//             connect(compile(expr.expr, loop), loop)
//             return [edge(loop)]
//         } else if (expr.type == "opt") {
//             return [edge(from)].concat(compile(expr.expr, from))
//         } else if (expr.type == "range") {
//             let cur = from
//                     for (let i = 0; i < expr.min; i++) {
//                 let next = node()
//                 connect(compile(expr.expr, cur), next)
//                 cur = next
//             }
//             if (expr.max == -1) {
//                 connect(compile(expr.expr, cur), cur)
//             } else {
//                 for (let i = expr.min; i < expr.max; i++) {
//                     let next = node()
//                     edge(cur, next)
//                     connect(compile(expr.expr, cur), next)
//                     cur = next
//                 }
//             }
//             return [edge(cur)]
//         } else if (expr.type == "name") {
//             return [edge(from, undefined, expr.value)]
//         } else {
//             throw new Error("Unknown expr type")
//         }
//     }
// }
// endregion
// Construct an NFA from an expression as returned by the parser. The NFA is represented as an array
// of states, which are themselves arrays of edges, which are `{term, to}` objects. The first state
// is the entry state and the last node is the success state.
//
// Note that unlike typical NFAs, the edge ordering in this one is significant, in that it is used
// to contruct filler content when necessary.
fun nfa(expr: Expr): List<List<Edge>> {
    val nfa = mutableListOf<MutableList<Edge>>(mutableListOf())
    connect(compile(nfa, expr, 0), node(nfa))
    return nfa
}

fun node(nfa: MutableList<MutableList<Edge>>): Int {
    nfa.add(mutableListOf())
    return nfa.size - 1
}

fun edge(nfa: MutableList<MutableList<Edge>>, from: Int, to: Int? = null, term: NodeType? = null): Edge {
    val edge = Edge(term, to)
    nfa[from].add(edge)
    return edge
}

fun connect(edges: List<Edge>, to: Int) {
    edges.forEach { edge -> edge.to = to }
}

fun compile(nfa: MutableList<MutableList<Edge>>, expr: Expr, from: Int): List<Edge> {
    var from = from
    when (expr) {
        is Expr.Choice -> {
            val res = mutableListOf<Edge>()
            for (expr in expr.exprs) {
                res.addAll(compile(nfa, expr, from))
            }
            return res
        }
        is Expr.Seq -> {
            var i = 0
            while (true) {
                val next = compile(nfa, expr.exprs[i], from)
                if (i == expr.exprs.size - 1) return next
                from = node(nfa)
                connect(next, from)
                i++
            }
        }
        is Expr.Star -> {
            val loop = node(nfa)
            edge(nfa, from, loop)
            connect(compile(nfa, expr.expr, loop), loop)
            return listOf(edge(nfa, loop))
        }
        is Expr.Plus -> {
            val loop = node(nfa)
            connect(compile(nfa, expr.expr, from), loop)
            connect(compile(nfa, expr.expr, loop), loop)
            return listOf(edge(nfa, loop))
        }
        is Expr.Opt -> {
            return listOf(edge(nfa, from)) + compile(nfa, expr.expr, from)
        }
        is Expr.Range -> {
            var cur = from
            for (i in 0 until expr.min) {
                val next = node(nfa)
                connect(compile(nfa, expr.expr, cur), next)
                cur = next
            }
            if (expr.max == -1) {
                connect(compile(nfa, expr.expr, cur), cur)
            } else {
                for (i in expr.min until expr.max) {
                    val next = node(nfa)
                    edge(nfa, cur, next)
                    connect(compile(nfa, expr.expr, cur), next)
                    cur = next
                }
            }
            return listOf(edge(nfa, cur))
        }
        is Expr.Name -> {
            return listOf(edge(nfa, from, null, expr.value))
        }
    }
}

// function cmp(a: number, b: number) { return b - a }
fun cmp(a: Int, b: Int): Int { return b - a }

// // Get the set of nodes reachable by null edges from `node`. Omit
// // nodes with only a single null-out-edge, since they may lead to
// // needless duplicated nodes.
// function nullFrom(nfa: Edge[][], node: number): readonly number[] {
//     let result: number[] = []
//     scan(node)
//     return result.sort(cmp)
//
//     function scan(node: number): void {
//         let edges = nfa[node]
//         if (edges.length == 1 && !edges[0].term) return scan(edges[0].to!)
//         result.push(node)
//         for (let i = 0; i < edges.length; i++) {
//         let {term, to} = edges[i]
//         if (!term && result.indexOf(to!) == -1) scan(to!)
//     }
//     }
// }
// Get the set of nodes reachable by null edges from `node`. Omit nodes with only a single
// null-out-edge, since they may lead to needless duplicated nodes.
fun nullFrom(nfa: List<List<Edge>>, node: Int): List<Int> {
    val result = mutableListOf<Int>()
    var scan: ((node: Int) -> Unit)? = null
    scan = { node: Int ->
        val edges = nfa[node]
        if (edges.size == 1 && edges[0].term == null) {
            scan?.invoke(edges.first().to!!)
        } else {
            result.add(node)
            edges.forEach {
                val term = it.term
                val to = it.to
                if (term == null && result.indexOf(to!!) == -1) {
                    scan?.invoke(to)
                }
            }
        }
    }
    scan(node)
    result.sortWith(Comparator(::cmp))
    return result.toList()
}

// // Compiles an NFA as produced by `nfa` into a DFA, modeled as a set
// // of state objects (`ContentMatch` instances) with transitions
// // between them.
// function dfa(nfa: Edge[][]): ContentMatch {
//     let labeled = Object.create(null)
//     return explore(nullFrom(nfa, 0))
//
//     function explore(states: readonly number[]) {
//         let out: [NodeType, number[]][] = []
//         states.forEach(node => {
//             nfa[node].forEach(({term, to}) => {
//                 if (!term) return
//                 let set: number[] | undefined
//                 for (let i = 0; i < out.length; i++) if (out[i][0] == term) set = out[i][1]
//                 nullFrom(nfa, to!).forEach(node => {
//                     if (!set) out.push([term, set = []])
//                     if (set.indexOf(node) == -1) set.push(node)
//                 })
//             })
//         })
//         let state = labeled[states.join(",")] = new ContentMatch(states.indexOf(nfa.length - 1) > -1)
//         for (let i = 0; i < out.length; i++) {
//         let states = out[i][1].sort(cmp)
//         state.next.push({type: out[i][0], next: labeled[states.join(",")] || explore(states)})
//     }
//         return state
//     }
// }
// Compiles an NFA as produced by `nfa` into a DFA, modeled as a set of state objects
// (`ContentMatch` instances) with transitions between them.
fun dfa(nfa: List<List<Edge>>): ContentMatch {
    val labeled = mutableMapOf<String, ContentMatch>()
    return explore(nfa, labeled, nullFrom(nfa, 0))
}

@Suppress("NestedBlockDepth")
fun explore(nfa: List<List<Edge>>, labeled: MutableMap<String, ContentMatch>, states: List<Int>): ContentMatch {
    val out = mutableListOf<Pair<NodeType, MutableList<Int>>>()
    states.forEach { node ->
        nfa[node].forEach inner@{
            val term = it.term ?: return@inner
            val to = it.to
            var set: MutableList<Int>? = null
            for (i in 0 until out.size) {
                if (out[i].first == term) {
                    set = out[i].second
                }
            }
            nullFrom(nfa, to!!).forEach { node ->
                if (set == null) {
                    set = mutableListOf()
                    out.add(term to set!!)
                }
                if (set!!.indexOf(node) == -1) {
                    set!!.add(node)
                }
            }
        }
    }
    val state = ContentMatch(states.indexOf(nfa.size - 1) > -1)
    labeled[states.joinToString(",")] = state
    for (i in 0 until out.size) {
        val states = out[i].second.sortedWith(::cmp)
        state.next.add(
            MatchEdge(
                type = out[i].first,
                next = labeled[states.joinToString(",")] ?: explore(nfa, labeled, states)
            )
        )
    }
    return state
}

// function checkForDeadEnds(match: ContentMatch, stream: TokenStream) {
//     for (let i = 0, work = [match]; i < work.length; i++) {
//         let state = work[i], dead = !state.validEnd, nodes = []
//         for (let j = 0; j < state.next.length; j++) {
//         let {type, next} = state.next[j]
//         nodes.push(type.name)
//         if (dead && !(type.isText || type.hasRequiredAttrs())) dead = false
//         if (work.indexOf(next) == -1) work.push(next)
//     }
//         if (dead) stream.err("Only non-generatable nodes (" + nodes.join(", ") + ") in a required position
//              (see https://prosemirror.net/docs/guide/#generatable)")
//     }
// }
fun checkForDeadEnds(match: ContentMatch, stream: TokenStream) {
    val work = mutableListOf(match)
    var i = 0
    while (i < work.size) {
        val state = work[i]
        var dead = !state.validEnd
        val nodes = mutableListOf<String>()
        state.next.forEach {
            val type = it.type
            val next = it.next
            nodes.add(type.name)
            if (dead && !(type.isText || type.hasRequiredAttrs())) {
                dead = false
            }
            if (work.indexOf(next) == -1) work.add(next)
        }
        if (dead) {
            stream.err(
                "Only non-generatable nodes (" + nodes.joinToString(", ") + ") in a required " +
                    "position (see https://prosemirror.net/docs/guide/#generatable)"
            )
        }
        i++
    }
}
