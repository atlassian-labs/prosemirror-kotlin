import {schema as bSchema} from "prosemirror-schema-basic"
import {addListNodes} from "prosemirror-schema-list"
import {Schema} from "prosemirror-model"

import {builders, NodeBuilder, MarkBuilder} from "./build"
export {builders, NodeBuilder, MarkBuilder} from "./build"

export const schema = new Schema({
  nodes: addListNodes(bSchema.spec.nodes, "paragraph block*", "block"),
  marks: bSchema.spec.marks
})

let b = builders(schema, {
  p: {nodeType: "paragraph"},
  pre: {nodeType: "code_block"},
  h1: {nodeType: "heading", level: 1},
  h2: {nodeType: "heading", level: 2},
  h3: {nodeType: "heading", level: 3},
  li: {nodeType: "list_item"},
  ul: {nodeType: "bullet_list"},
  ol: {nodeType: "ordered_list"},
  br: {nodeType: "hard_break"},
  img: {nodeType: "image", src: "img.png"},
  hr: {nodeType: "horizontal_rule"},
  a: {markType: "link", href: "foo"},
}) as any

export function eq<T extends {eq(other: T): boolean}>(a: T, b: T): boolean { return a.eq(b) }

export const doc: NodeBuilder = b.doc
export const p: NodeBuilder = b.p
export const code_block: NodeBuilder = b.code_block
export const pre: NodeBuilder = b.pre
export const h1: NodeBuilder = b.h1
export const h2: NodeBuilder = b.h2
export const h3: NodeBuilder = b.h3
export const li: NodeBuilder = b.li
export const ul: NodeBuilder = b.ul
export const ol: NodeBuilder = b.ol
export const img: NodeBuilder = b.img
export const hr: NodeBuilder = b.hr
export const br: NodeBuilder = b.br
export const blockquote: NodeBuilder = b.blockquote
export const a: MarkBuilder = b.a
export const em: MarkBuilder = b.em
export const strong: MarkBuilder = b.strong
export const code: MarkBuilder = b.code
