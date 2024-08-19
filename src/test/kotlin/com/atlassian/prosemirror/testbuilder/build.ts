import {Node, NodeType, MarkType, Schema, Attrs} from "prosemirror-model"

type Tags = {[tag: string]: number}

export type ChildSpec = string | Node | {flat: readonly Node[], tag: Tags}

const noTag = (Node.prototype as any).tag = Object.create(null)

function flatten(
  schema: Schema,
  children: ChildSpec[],
  f: (node: Node) => Node
): {nodes: Node[], tag: Tags} {
  let result = [], pos = 0, tag = noTag

  for (let i = 0; i < children.length; i++) {
    let child = children[i]
    if (typeof child == "string") {
      let re = /<(\w+)>/g, m, at = 0, out = ""
      while (m = re.exec(child)) {
        out += child.slice(at, m.index)
        pos += m.index - at
        at = m.index + m[0].length
        if (tag == noTag) tag = Object.create(null)
        tag[m[1]] = pos
      }
      out += child.slice(at)
      pos += child.length - at
      if (out) result.push(f(schema.text(out)))
    } else {
      if ((child as any).tag && (child as any).tag != (Node.prototype as any).tag) {
        if (tag == noTag) tag = Object.create(null)
        for (let id in (child as any).tag)
          tag[id] = (child as any).tag[id] + ((child as any).flat || (child as any).isText ? 0 : 1) + pos
      }
      if ((child as any).flat) {
        for (let j = 0; j < (child as any).flat.length; j++) {
          let node = f((child as any).flat[j])
          pos += node.nodeSize
          result.push(node)
        }
      } else {
        let node = f(child as Node)
        pos += node.nodeSize
        result.push(node)
      }
    }
  }
  return {nodes: result, tag}
}

function id<T>(x: T): T { return x }

function takeAttrs(attrs: Attrs | null, args: [a?: Attrs | ChildSpec, ...b: ChildSpec[]]) {
  let a0 = args[0]
  if (!args.length || (a0 && (typeof a0 == "string" || a0 instanceof Node || a0.flat)))
    return attrs

  args.shift()
  if (!attrs) return a0 as Attrs
  if (!a0) return attrs
  let result: Attrs = {}
  for (let prop in attrs) (result as any)[prop] = attrs[prop]
  for (let prop in a0 as Attrs) (result as any)[prop] = (a0 as Attrs)[prop]
  return result
}

export type NodeBuilder = (attrsOrFirstChild?: Attrs | ChildSpec, ...children: ChildSpec[]) => Node
export type MarkBuilder = (attrsOrFirstChild?: Attrs | ChildSpec, ...children: ChildSpec[]) => ChildSpec

/// Create a builder function for nodes with content.
function block(type: NodeType, attrs: Attrs | null = null): NodeBuilder {
  let result: NodeBuilder = function(...args) {
    let myAttrs = takeAttrs(attrs, args)
    let {nodes, tag} = flatten(type.schema, args as ChildSpec[], id)
    let node = type.create(myAttrs, nodes)
    if (tag != noTag) (node as any).tag = tag
    return node
  }
  if (type.isLeaf) try { (result as any).flat = [type.create(attrs)] } catch(_) {}
  return result
}

// Create a builder function for marks.
function mark(type: MarkType, attrs: Attrs | null): MarkBuilder {
  return function(...args) {
    let mark = type.create(takeAttrs(attrs, args))
    let {nodes, tag} = flatten(type.schema, args as ChildSpec[], n => {
      let newMarks = mark.addToSet(n.marks)
      return newMarks.length > n.marks.length ? n.mark(newMarks) : n
    })
    return {flat: nodes, tag}
  }
}

export function builders(schema: Schema, names?: {[name: string]: Attrs}) {
  let result = {schema}
  for (let name in schema.nodes) (result as any)[name] = block(schema.nodes[name], {})
  for (let name in schema.marks) (result as any)[name] = mark(schema.marks[name], {})

  if (names) for (let name in names) {
    let value = names[name], typeName = value.nodeType || value.markType || name, type
    if (type = schema.nodes[typeName]) (result as any)[name] = block(type, value)
    else if (type = schema.marks[typeName]) (result as any)[name] = mark(type, value)
  }
  return result
}
