# Better Redstone Wire

improved algorithm for redstone wire ( powering / depowering / update )

## Feature List

- [x] Powering
    - [x] Hard power from block
    - [x] Hard power from repeater
    - [x] Hard power from comparator
    - [x] Hard power from observer
    - [x] Soft power from solid blocks
    - [x] Ignore soft power from other dust
- [x] De-Powering
    - [X] Single block update to final value
    - [x] Deterministic update order
- [x] Update
    - [x] Comparator change value
    - [x] Added dust to circuit
    - [x] Removed dust from circuit

## Known Drawbacks

- Redstone dust is now a BlockEntity
- When updated for the first time with the mod all powered dust sends a block update

### Comment

- Save format 100% compatible with vanilla ( you can remove the mod and the save file will work in a fully vanilla
  environment )

## Known Bugs

- [ ]  **General**
    - [ ] **MAJOR**: Update suppressing the removal of a redstone dust might permanently bud the connected dust



