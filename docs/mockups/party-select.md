# Party select (using an item)

Cobblemon's `PartySelectGUI`, shown when a medicine, candy, vitamin, mint or evolution item is used: pick the party
Pokémon it goes to. Vanilla clients get it as one chest row, which Java draws and Geyser turns into a Bedrock chest:

```
┌ Use Potion on… ──────────────────────────────────────────┐
│      [Pikachu ♂ Lv.15] [Bulbasaur Lv.8] [ ▒ Rattata ]   │
│                                                          │
└──────────────────────────────────────────────────────────┘
  slot 0 empty, the party from slot 1; each icon is the Pokémon's portrait with its HP, types, nature and moves
  in the tooltip; one the item can't help is a grey pane ("It won't have any effect.").
```

In battle the same row lists the battle team; picking one sends Cobblemon's bag item to Showdown, which takes the
turn. Ether and Max Ether open a second row with the Pokémon's moves and their PP.
