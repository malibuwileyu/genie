# Genie 🧞

> Never lose a thought again

Built for [Code Spring Hackathon](https://code-spring.devpost.com/) | Productivity Track

## What it does

**Genie** is a desktop productivity app that protects your mental flow in two ways:

1. **Context Resurrection** — When you get interrupted, hit a hotkey to save your "mental state" (clipboard, active window, what you were doing). When you return, Genie shows you exactly where you left off.

2. **Curiosity Capture** — Ever think "I wish I knew how X works" but don't want to break focus? Hit a hotkey, speak or type your curiosity, and keep working. Later, Genie delivers a mini research article on each topic.

## Features

- ✨ **Global Hotkeys** — Works from any app (Cmd/Ctrl+Shift+S to save, Cmd/Ctrl+Shift+W to wish)
- ✨ **System Tray** — Always accessible, never in the way
- ✨ **Context Capture** — Saves clipboard, active window, timestamp
- ✨ **Curiosity Queue** — Never lose a "I wonder..." thought again
- ✨ **AI Research** — Generates ELI5-style articles on your queued topics
- ✨ **Cross-Platform** — Works on Mac, Windows, and Linux

## Demo

🎬 [Video Demo](TODO)  
📦 [Download](TODO)

## Screenshots

<!-- Add screenshots here -->
![System Tray](docs/screenshots/tray.png)
![Context Restore](docs/screenshots/context.png)
![Wish Queue](docs/screenshots/wishes.png)

## How we built it

- **Language**: Java 17
- **UI**: JavaFX 21 with custom CSS
- **Global Hotkeys**: JNativeHook
- **Storage**: SQLite
- **AI**: OpenAI GPT-4o-mini

## Challenges we faced

<!-- Fill in during development -->

## What we learned

<!-- Fill in during development -->

## What's next

- Browser extension for tab capture
- Voice input for wishes
- Interrupt analytics (who/what breaks your flow most)
- Spaced repetition for revisiting wishes
- Export to Obsidian/Notion

## Setup

### Requirements
- Java 17 or higher
- OpenAI API key

### Run from Source

```bash
# Clone the repo
git clone https://github.com/yourusername/genie.git
cd genie

# Build
mvn clean package

# Run
mvn javafx:run
```

### Download Installer

- **Mac**: [Genie.dmg](TODO)
- **Windows**: [Genie.exe](TODO)
- **Linux**: [genie.deb](TODO)

### Configuration

On first run, Genie will ask for your OpenAI API key. Get one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).

## Hotkeys

| Action | Mac | Windows/Linux |
|--------|-----|---------------|
| Save Context | Cmd+Shift+S | Ctrl+Shift+S |
| Make a Wish | Cmd+Shift+W | Ctrl+Shift+W |
| Open Genie | Click tray icon | Click tray icon |

## Team

- Ryan Heron — Developer

## License

MIT

