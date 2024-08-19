An implementation of an undo/redo history for ProseMirror. This
history is _selective_, meaning it does not just roll back to a
previous state but can undo some changes while keeping other, later
changes intact. (This is necessary for collaborative editing, and
comes up in other situations as well.)

@history

@undo

@redo

@undoDepth

@redoDepth

@closeHistory

### Versioning

- Implemented changes to match version [1.4.1](https://github.com/ProseMirror/prosemirror-history/releases/tag/1.4.1) 
of prosemirror-history