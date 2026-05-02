---
status: done
execution_mode: one-shot
---

# Fix: Delete Dialog Missing Backdrop Overlay

## Intent

The delete confirmation dialog on desktop appears to float inline over the book list with no dark backdrop, making it look like an inline popover rather than a proper modal.

## Root Cause

`DialogOverlay` in `src/components/ui/dialog.tsx` is missing the `bg-black/80` Tailwind class. The standard shadcn/ui implementation includes this to create the semi-transparent dark backdrop that visually separates the modal from the content behind it.

## Fix

Add `bg-black/80` to the `DialogOverlay` className.

**File:** `src/components/ui/dialog.tsx`, line 22

Before:
```
"fixed inset-0 z-50 data-[state=open]:animate-in ..."
```

After:
```
"fixed inset-0 z-50 bg-black/80 data-[state=open]:animate-in ..."
```
