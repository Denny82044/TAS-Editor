# TAS-Editor

A modified fork of TAS-Editor specifically for the Raspberry Pi Pico 2, with direct USB script uploading, configurable playback speed, looping, and controller pause/disconnect options. This project makes real-hardware TASing on Switch 1 and 2 possible without homebrew (assuming you have a Raspberry Pi Pico 2).

This project uses no form of soldering. All actions are performed with the BOOTSEL button on the Pico 2.

# Setup and Usage

## Prerequisites

* Java 8 or newer (Java 17+ recommended)
* Raspberry Pi Pico 2
* A way to connect your Pico to your Switch and PC (usually a USB-A to Micro-USB cable, but this is dependent on your setup)

## Setup
1. Flash your Pico 2 with the latest .UF2 firmware located in the latest release.
2. Download or build (instructions below) the latest release of the TAS-Editor.
3. Run TAS-Editor.jar, and enjoy!

## Usage
Pretty much exactly the same as MonsterDruide1's version, just with added features specifically for the custom Pico 2 firmware (it's very self explanatory).

However, to operate the Pico 2 itself, the playback controls go as the following:
*Pause/Resume: Single click of BOOTSEL
*Reset/Reload Script: Quick double click of BOOTSEL

**IMPORTANT:** if you upload a script that only consists of stick inputs, and your Pico 2 isn't being detected as a controller on your switch, you must first add some form of button action before the stick inputs. This is because stick inputs alone aren't enough to "initialise" the controller. Also, if you flash the firmware to the Pico 2 without first uploading a script, trying to press BOOTSEL to play a script will most likely do nothing.


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
To report a bug, create a post in [Issues](https://github.com/denny82044/TAS-Editor/issues).

# AI Usage
AI coding tools were used in the making of this program.
