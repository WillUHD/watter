<img align="left" width="128" height="128" src="https://github.com/user-attachments/assets/ced6fc7b-3fa8-400d-8365-2f21e9f9936e" alt="Watter">

<div align="left">

# Watter
A simple utility for monitoring the current wattage, CPU, GPU, and charging status of your Mac. 
 
<div align="left">

#

### How it Works
Watter uses the commandline function `powermetrics` to get information directly from the SMC controller of your Mac. It also uses `pmset` to get the charging estimations. 

It then displays the information onto the menubar, and you can customize what information to show. Compared to their commandline counterparts, Watter offers a nice abstraction for users who want a quick glance at their menubar for current system status. 

⚠️ This only works on **Intel Macs** for now! Working on an Apple Silicon reader and parser for its commandline utility function. 

# 
<img width="368" alt="image" src="https://github.com/user-attachments/assets/0194304a-098b-42e8-8fb8-edfe519e85d2" />

> You can decide what to view in Watter based on your preference. 

#

<img width="263" alt="image" src="https://github.com/user-attachments/assets/c389ae50-8fe4-4b94-b73d-512f08f425a3" />

> This is literally it. Watter is that easy. 

### Collaboration
I'm open to any suggestions on improving the app's codebase and making everything work more seamlessly. If you have a fix or suggestion, feel free to tell me. 

### Release Changelog
<details>
    <summary>Jun 15, 2025</summary>
        Finalized the first version of Watter as a menubar app. 
</details>

### Future Direction
1. **Support for ARM-based Macs**: Watter currently only supports parsing the commandline from Intel Macs. A future release will provide support for ARM Macs as well, since ARM Macs have a slightly different `powermetrics` output style.
2. **Use without admin password**: Currently, Watter needs your admin password to function. I could add another binary that talks to the SMC without the need for an admin password or find another way. However, currently, `powermetrics` is the safest way to do everything. I could also store the admin password once in Keychain for ease of use, however I'm still exploring options as to how to do this.
3. **Context-aware backgrounds**: It requests for screen recording permissions to adapt contrast within the menubar, depending on wallpaper luminosity. This is because it doesn't rely on the OS's drawing techniques (which handle this automatically). It also can't detect whether if the menubar has lost its focus and only adapts to the current focused display. Ideally, it can have separate colors for each display on different colors if necessary and decrease the transparency when a display has lost its focus. 
4. **Proper quitting strategy**: Looking for a solution to close the process when force quit.
5. **Just use swift**: Java is too bad for UIs. Nearly 90% CPU time is spent updating the UI. Perhaps switch to Swift for the frontend? 


### Credits
<div align="center">

![Group](https://github.com/user-attachments/assets/d36f93b4-710b-4fbe-92f0-b55a40d7eb86)

> by WillUHD
