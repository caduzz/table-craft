# Future Mod

Future Mod is a personal Minecraft mod project I developed in my free time to revisit and practice Java. The goal was not to build something massive, but to challenge myself by working directly with Minecraft’s modding ecosystem and understanding how everything connects under the hood.

## Motivation

I started this project mainly to:

- Refresh my Java knowledge  
- Explore NeoForge modding  
- Understand how Minecraft systems (items, armor, tools, events, world generation, etc.) actually work  
- Push myself to solve real-world coding issues instead of just following tutorials  

One thing I quickly noticed:  
Minecraft modding relies heavily on JSON for almost everything — recipes, loot tables, world generation, block states, models, and more.

While it was a bit frustrating at first (there is a lot of JSON), it also helped me better understand how data-driven systems work and why Minecraft is structured this way.

---

## Features Implemented

Throughout development, I experimented with:

- Custom Armor (with custom materials and attributes)  
- Custom Tools (Pickaxe, Axe, Hoe, etc.)  
- Custom Tiers  
- Attribute Modifiers  
- Enchantability fixes  
- Block drop configuration  
- Ore world generation  
- Custom Keybind (Zoom with smooth FOV transition)  
- Fall damage modification (bounce / cancel logic)  
- Custom movement sound behavior  
- Equipment-based effects (Fire Resistance, Jump Boost, etc.)

---

## Challenges Faced

Some of the main challenges included:

- Understanding NeoForge's event system  
- Working with `DeferredRegister` and registries  
- Fixing attribute modifiers not applying properly  
- Ensuring tools dropped correct blocks (Tier & Tag system)  
- Making armor non-stackable  
- Handling client vs server side logic  
- Implementing smooth zoom without FPS dependency  
- Dealing with API changes between Minecraft versions  
- Fixing compilation errors caused by method signature changes  
- Understanding how `Holder`, `TagKey<Block>`, and registries work  

And of course, a significant amount of JSON configuration.

---

## What I Learned

This project helped me:

- Get more comfortable with Java  
- Improve debugging skills  
- Understand Minecraft's architecture better  
- Work with event-driven programming  
- Deal with version migration issues  
- Think more structurally about modular code  

---

## Project Status

This is an experimental learning project.  
It is not meant to be a polished public release — it is a sandbox for experimenting and improving my understanding of Java and Minecraft modding.

---

## Tech Stack

- Java  
- NeoForge  
- Minecraft Modding API  
- Gradle  
