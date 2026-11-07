# DJ Portable Pro Design Tokens

This document serves as the single source of truth for visual consistency between Android (XML/Views) and potential future Web (React/Tailwind) implementations.

## Colors

| Token | Hex | Usage | Tailwind Class |
|-------|-----|-------|----------------|
| `dj-background` | `#050507` | Base background | `bg-[#050507]` |
| `dj-panel-bg` | `#0D0D11` | Containers/Panels | `bg-[#0D0D11]` |
| `dj-surface-control` | `#1A1A22` | Control surface | `bg-[#1A1A22]` |
| `dj-surface-active` | `#25252D` | Active control highlight | `bg-[#25252D]` |
| `dj-surface-pressed` | `#32323D` | Interaction (Pressed) | `bg-[#32323D]` |
| `dj-cyan` | `#00E5FF` | Deck A / Info Accent | `text-[#00E5FF]` |
| `dj-magenta` | `#FF00FF` | Deck B Accent | `text-[#FF00FF]` |
| `dj-orange-master` | `#FF9100` | Master / Warning | `text-[#FF9100]` |
| `dj-green-status` | `#00E676` | Play / Success | `text-[#00E676]` |
| `dj-red-alert` | `#FF1744` | Alarms / Error | `text-[#FF1744]` |
| `dj-border` | `#22222A` | Default border | `border-[#22222A]` |

## Spacing & Dimensions

- `micro`: 2px
- `tiny`: 4px
- `small`: 8px
- `medium`: 12px
- `large`: 16px
- `xlarge`: 24px

## Typography

- `huge`: 32px (Display)
- `display`: 20px (Headers)
- `title`: 16px
- `subhead`: 14px
- `body`: 12px
- `caption`: 10px
- `tag`: 9px
- `micro`: 8px

## Corner Radius

- `small`: 2px (Controls)
- `medium`: 4px (Panels)
- `large`: 8px (Main UI blocks)

## Tailwind Configuration Suggestion

```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        dj: {
          background: '#050507',
          panel: '#0D0D11',
          surface: '#1A1A22',
          active: '#25252D',
          pressed: '#32323D',
          cyan: '#00E5FF',
          magenta: '#FF00FF',
          orange: '#FF9100',
          green: '#00E676',
          red: '#FF1744',
          border: '#22222A',
        }
      },
      borderRadius: {
        'dj-sm': '2px',
        'dj-md': '4px',
        'dj-lg': '8px',
      }
    }
  }
}
```
