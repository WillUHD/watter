![newwatt](https://github.com/user-attachments/assets/5591ce5a-767e-409d-9bb0-ee9e0c50492a)

<div align="center">

# Watter
A simple utility for monitoring the current wattage, CPU, GPU, and charging status of your Mac. 

<div align="left">

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
2. **Security issue**: For ease of development, Watter requests the user for an admin password. It then uses the key command `echo "password" | sudo -S zsh -c powermetrics ...` which will show the user's password in Activity Monitor as a command process, leaving a crucial security vulnerability. To mitigate this I could add the current process as a `sudoer` in the `sudoers` file concealing the application, as `powermetrics` is usually not something to be worried about in terms of getting automatic `sudo` permission or not
3. **Use without admin password**: Currently, Watter needs your admin password to function. I could add another binary that talks to the SMC without the need for an admin password or find another way. However, currently, `powermetrics` is the safest way to do everything. I could also store the admin password once in Keychain for ease of use, however I'm still exploring options as to how to do this.
4. **Context-aware backgrounds**: In Swift, the OS decides what color to paint text/graphics depending on the wallpaper color, to increase contrast. So if the wallpaper is dark, the menubar text is going to be white. If the wallpaper is bright, the menubar text is going to be black. I have no way of doing this unless I enable screen record functions, and it's not even possible using JNA. 

### Credits
<div align="center">

![Group](https://github.com/user-attachments/assets/d36f93b4-710b-4fbe-92f0-b55a40d7eb86)

> by WillUHD
