# prosemirror-test-builder

This module provides helpers for building ProseMirror documents for
tests. It's main file exports a basic schema with list support, and a
number of functions, whose name mostly follows the corresponding HTML
tag, to create nodes and marks in this schema

Node builder functions optionally take an attribute object as their
first argument, followed by zero or more child nodes, and return a
node with those attributes and children. Children should be either
strings (for text nodes), existing nodes, or the result of calling a
mark builder. Mark builder functions work similarly, but return an
object representing a set of nodes rather than a single node.

These builders help specifying and retrieving positions in the
documents that you created (to avoid needing to count tokens when
writing tests). Inside of strings passed as child nodes,
angle-brackets `<name>` syntax can be used to place a tag called
`name` at that position. The angle-bracketed part will not appear in
the result node, but is stored in the node's `tag` property, which is
an object mapping tag names to position integers. A string which is
_only_ a tag or set of tags may appear everywhere, even in places
where text nodes aren't allowed.

So if you've imported `doc` and `p` from this module, the expression
`doc(p("foo<a>"))` will return a document containing a single
paragraph, and its `.tag.a` will hold the number 4 (the position at
the end of the paragraph).

In addition to defining a function for each mark and node name in the
test schema, the module exports the following helpers:

**`schema`**: The test schema itself.

**`p`**: A builder for paragraph nodes.

**`pre`**: A builder for code block nodes.

**`h1`**: A builder for heading block nodes with the `level` attribute defaulting to 1.

**`h2`**: A builder for heading block nodes with the `level` attribute defaulting to 2.

**`h3`**: A builder for heading block nodes with the `level` attribute defaulting to 3.

**`li`**: A builder for list item nodes.

**`ol`**: A builder for ordered list nodes.

**`ul`**: A builder for bullet list nodes.

**`br`**: A builder for hard break nodes.

**`img`**: A builder for image nodes, with the `src` attribute defaulting to `"img.png"`.

**`hr`**: A builder for horizontal rule nodes.

**`a`**: A builder for link marks.

The package also exports the following helpers:

**`builders`**`(schema: Schema, names?: Object<Attrs>) → Object`

Create a object of builders for a custom schema. Will return an object
with a `schema` property and one builder for each node and mark in the
schema. The second argument can be used to add custom builders—if
given, it should be an object mapping names to attribute objects,
which may contain a `nodeType` or `markType` property to specify which
node or mark the builder by this name should create.

**`eq`**`(a, b) → boolean`

Calls `a.eq(b)`. Can be useful to pass as comparison predicate when
comparing ProseMirror nodes or slices.

## Maven / Gradle dependency

Check the latest package at Maven central on: https://packages.atlassian.com/maven-central/com/atlassian/prosemirror/test-builder.

### Maven:
```xml
<dependency>
    <groupId>com.atlassian.prosemirror</groupId>
    <artifactId>test-builder</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle:
```kotlin
implementation("com.atlassian.prosemirror:test-builder:1.0.1")
```
