# SELLO-117 — Keep category amounts whole in Recibo's grid

`CategoryCell` (design-system `Category.kt`) now shows a no-limit category's spent amount on its own line with a small caption under it (`amountCaption`, "gastado"), as in the v2 Recibo grid; before, "gastado" sat beside the amount and squeezed it to one character per line in a 320dp grid column. Recibo, the catalog and the prototype pass the caption.
Still open: nothing.
