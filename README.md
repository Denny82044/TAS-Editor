# TAS-Editor

A modified fork of TAS-Editor specifically for the Raspberry Pi Pico 2, with direct USB script uploading, configurable playback speed, looping, and controller pause/disconnect options. This project makes real-hardware TASing on Switch 1 and 2 possible (assuming you have a Raspberry Pi Pico 2).

# Building

## Prerequisites

* JDK 8 or newer (capable of compiling Java 8 source)
* Maven
* A clone of this repository

## Instructions (Windows)

1. Navigate to the project root.
2. Open a CMD prompt and build the project:
```
mvn clean package
```
3. After the build succeeds, navigate to the /target folder.
4. The compiled application will be located there.

# Support
To report a bug, create a post in [Issues](https://github.com/MonsterDruide1/TAS-Editor/issues).
