# Trade

Cobblemon's trade between two players, as a 6-row chest (Java draws it, Geyser makes it a Bedrock chest).
`/trade <player>` sends a request with a clickable [Accept]; both players then see:

```
┌ Trade with Steve ────────────────────────────────────────┐
│ [Pika][Bulb][Ratt][    ][    ][    ][    ][    ][Ready?]│  your party: click one to offer   Ready (red/green)
│ [    ][    ][    ][    ][    ][    ][    ][    ][Cancel]│
│ [    ][    ][Your offer ][    ][ ⇄ ][    ][Steve's offer][    ][    ]│  the two offers
│ [    ][    ][    ][    ][    ][    ][    ][    ][    ]│
│ [    ][    ][    ][    ][    ][    ][    ][    ][    ]│
│ [Eeve][Abra][    ][    ][    ][    ][    ][    ][    ]│  their party (view only)
└──────────────────────────────────────────────────────────┘
```

Changing an offer clears both Ready marks. When both are ready the Pokémon swap; trade evolutions (Machoke,
Kadabra, Onix holding a Metal Coat...) happen as they arrive. Closing the chest cancels the trade for both.
