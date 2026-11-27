This is a core module of ProseMirror. ProseMirror is a well-behaved rich semantic content editor based on contentEditable, 
with support for collaborative editing and custom document schemas.

## Versioning
This module is a port of version [1.24.1](https://github.com/ProseMirror/prosemirror-model/releases/tag/1.24.1)
  of prosemirror-model

## Recovering nodes and marks during JSON parsing

Schemas can opt into required-attribute recovery for all node and mark types:

```kotlin
val schema = Schema(spec, jsonParseRecovery = JsonParseRecovery())
```

Before decoding attributes or calling a typed creator, the policy checks the original JSON for
every attribute declared without a default (`AttributeSpec.hasDefault == false`). Missing required
attributes or a non-object `attrs` value route the input through the schema's unsupported factories.
Unfamiliar attributes are retained, and optional attributes can still use their defaults.

Fallback selection uses the existing schema configuration:

| Input | Fallback |
| --- | --- |
| Unknown or invalid mark | `SchemaSpec.unsupportedMark` (default `unsupportedMark`) |
| Invalid known node | Per-call or schema `unknownNodeFallback`, then its declared inline/block kind |
| Unknown node | Per-call or schema `unknownNodeFallback`, then `unsupportedNode` if `content` is present, otherwise `unsupportedInlineNode` |

`unsupportedNode` defaults to `unsupportedBlock`; `unsupportedInlineNode` defaults to
`unsupportedInline`. A per-call selector replaces the schema selector, including when it returns
`null`. The selector can choose more specific unsupported types, such as a block leaf or a container
whose children are inline.

Subclass `JsonParseRecovery` to supply additional format validation through `isValidNode` and
`isValidMark`. These methods see the complete original `JsonObject`, including absent fields,
explicit nulls, wrong value types, unfamiliar fields, and internal identity data. A format such as
ADF can require a field even if its ProseMirror specification provides a default. Presence alone
does not validate nullability, value types, or enums; those are separate format rules. Returning
`false` requests recovery. Policy and recovery-factory exceptions propagate. Once validation accepts
a known type, its creator failures also propagate instead of implicitly requesting recovery.

The default `createNode` and `createMark` recovery factories reuse `unknownNodeAttrs` and
`unknownMarkAttrs`. Applications can override these factories to retain raw input in their own
unsupported representations. For lossless serialization, preserve the original wrapper and whether
attributes were absent; serialize a recovered node's current parsed children and marks so edits
remain visible. Stable equality attributes let independently parsed add/remove steps match the
same recovered mark. Internal `NodeId` and `MarkId` are separate from equality. Configure recovery
mark exclusions and node mark sets to allow distinct unsupported marks to coexist.

The policy is shared by standalone nodes and marks, fragments, slices, and transform steps,
including both add/remove mark steps and replacement slices. It runs during tolerant parsing;
`check = true` additionally validates the resulting unsupported type. `withId = true` restores
internal identity as usual. Programmatic `NodeType.create` and `MarkType.create` bypass the policy,
so recovery factories can use them without recursion. Factories must not recursively parse the same
input. Existing `Schema(spec)` behavior and `SchemaSpec` signatures are unchanged.
