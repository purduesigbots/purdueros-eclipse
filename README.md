# PROS for Eclipse (purdueros-eclipse)
This repository contains the source code for the Eclipse feature than enables Eclipse programmers to work with the VEX Cortex Microcontroller and create and manage PROS projects.

## About Purdue Robotics Operating System
The centralized location for major PROS releases and kernel releases is at https://github.com/purduesigbots/purdueros. Documentation for PROS is available at https://purduesigbots.github.io/purdueros

PROS is a lightweight and fast alternative operating system for the VEX Cortex Microcontroller. It features multitasking, low-level control, and Wiring compatible functions to harness the full power of the Cortex. With a real-time kernel based on FreeRTOS (http://www.freertos.org) and a development environment built on Eclipse CDT (http://www.eclipse.org/), PROS uses proven software to increase the reliability and usability of the VEX development system across all three major computing platforms.

PROS is currently under active development to make it usable for VEX high school and middle school teams. PROS is intended for advanced VEX users seeking to move beyond competing environments.

Eclipse and FreeRTOS have their own licensing agreements. Please see http://www.eclipse.org/legal/epl-v10.html  and http://www.freertos.org/a00114.html  for details.

## About PROS for Eclipse
The Purdue Robotics Operating System feature combines three plugins that enable programmers to create PROS projects, interface with the VEX Cortex Microcontroller, and use a workspace well-suited for PROS development.

### CCIDELayout
The CCIDELayout (edu.purdue.sigbots.ros.eclipse.perspective) configures the PROS perspective in Eclipse. It configures the menus, commands, and other UI elements that the other plugins utilize.

### NewCortexProject
NewCortexProject is the wizard for creating a new PROS project and sets up a Project based on a template in the "sample" folder.

### PROSFeature
Feature that bundles the Eclipse plugins

### update-site
An Eclipse software site that hosts the PROS feature for future updates.

### VexFlashNG
Plugin that interfaces with the VEX Cortex Microcontroller

# Testing a Different Branch
It is possible to change where Eclipse looks for the PROS feature by modifying the software site url. Just change the site url to `https://raw.githubusercontent.com/purduesigbots/purdueros-eclipse/<branch_name>/update-site/site.xml`

# Contributing
PROS for Eclipse is an open-source, community developed feature. You are free to fork this repository and create a pull request to add a feature, fix a bug, or generally improve this repository.

# Reporting Issues
For Eclipse-related issues, report issues to this repository (https://github.com/purduesigbots/purdueros-eclipse/issues). For PROS (kernel) related issues, report to the main repository (https://github.com/purduesigbots/purdueros/issues).
