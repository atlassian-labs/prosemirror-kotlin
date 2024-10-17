An implementation of an undo/redo history for ProseMirror. This
history is _selective_, meaning it does not just roll back to a
previous state but can undo some changes while keeping other, later
changes intact. (This is necessary for collaborative editing, and
comes up in other situations as well.)

## Versioning
This module is a port of version [1.4.1](https://github.com/ProseMirror/prosemirror-history/releases/tag/1.4.1) 
of prosemirror-history
