# IRMusicSync

A passion project: An Android app that allows users to send IR signals to a LED strip controller synchronized to the phone's audio output (music mostly).

## 📋 About

IRMusicSync is an Android application that synchronizes infrared (IR) signals with your phone's audio output, primarily designed for music. The app analyzes audio in real-time and sends corresponding IR commands to LED strip controllers, creating a dynamic lighting experience that matches the rhythm and intensity of your music.

## 🎯 Features

- **Real-time Audio Analysis**: Captures and analyzes audio output from your device
- **IR Signal Transmission**: Sends synchronized IR commands to LED strip controllers
- **Music Synchronization**: LED patterns sync with music rhythm, beats, and intensity
- **Android Native**: Built with Kotlin for optimal performance
- **Easy to Use**: Simple interface for controlling your LED strips

## 🚀 How It Works

1. **Audio Capture**: The app captures audio output from your Android device
2. **Audio Analysis**: Real-time analysis of audio frequency, beats, and intensity
3. **Pattern Generation**: Generates lighting patterns based on audio characteristics
4. **IR Transmission**: Sends IR signals to your LED strip controller via the phone's IR blaster
5. **Synchronization**: LED colors and patterns change in sync with the music

## 📁 Project Structure

```
IRMusicSync/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Kotlin source files
│   │   │   └── res/             # Resources (layouts, drawables, etc.)
│   │   └── ...
├── gradle/                       # Gradle wrapper files
├── .idea/                        # Android Studio configuration
├── build.gradle.kts              # Project build configuration
├── settings.gradle.kts           # Gradle settings
├── gradle.properties            # Gradle properties
├── gradlew                      # Gradle wrapper (Unix)
└── gradlew.bat                  # Gradle wrapper (Windows)
```

## 🛠️ Technologies Used

- **Kotlin**: 100% Kotlin codebase for modern Android development
- **Android SDK**: Native Android application
- **IR API**: Android's ConsumerIrManager for IR signal transmission
- **Audio Processing**: Real-time audio analysis and processing
- **Gradle**: Build automation and dependency management

## 📦 Prerequisites

- **Android Studio**: Latest version recommended
- **Android Device**: Physical device with IR blaster (required for testing)
- **LED Strip Controller**: Compatible IR-controlled LED strip controller
- **Android SDK**: API level 21+ (Android 5.0 Lollipop or higher)

## 🔧 Installation

1. Clone the repository:
```bash
git clone https://github.com/OussemaBenAmeur/IRMusicSync.git
cd IRMusicSync
```

2. Open the project in Android Studio:
   - File → Open → Select the IRMusicSync directory
   - Wait for Gradle sync to complete

3. Connect an Android device with IR blaster:
   - Enable Developer Options on your device
   - Enable USB Debugging
   - Connect via USB

4. Build and run:
   - Click the Run button in Android Studio
   - Or use: `./gradlew installDebug`

## 💻 Usage

1. **Launch the App**: Open IRMusicSync on your Android device
2. **Grant Permissions**: Allow necessary permissions (audio, IR access)
3. **Start Music**: Play music on your device
4. **Position IR Blaster**: Point your phone's IR blaster at the LED controller
5. **Enjoy**: Watch your LED strips sync with the music!

## 📱 Requirements

### Device Requirements
- Android device with built-in IR blaster
- Android 5.0 (API 21) or higher
- Audio output capability

### Hardware Requirements
- IR-controlled LED strip controller
- LED strip lights
- Proper IR line-of-sight between phone and controller

## 🎨 Supported LED Controllers

The app is designed to work with common IR-controlled LED strip controllers. You may need to configure IR codes for your specific controller model.

## 🔍 Development

### Building from Source

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### Project Configuration

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: Latest Android version
- **Build Tool**: Gradle with Kotlin DSL

## 🐛 Known Limitations

- Requires a device with IR blaster (not all Android devices have this)
- IR range is limited (typically 5-10 meters)
- Requires line-of-sight between phone and LED controller
- Audio analysis performance may vary by device

## 🤝 Contributing

Contributions are welcome! This is a passion project, and any improvements are appreciated. Areas for contribution:

- Support for additional LED controller models
- Enhanced audio analysis algorithms
- UI/UX improvements
- Performance optimizations
- Bug fixes and stability improvements

Please feel free to submit a Pull Request!

## 📝 License

This project is open source and available for educational and personal use.

## 👤 Author

**Oussema Ben Ameur**
- GitHub: [@OussemaBenAmeur](https://github.com/OussemaBenAmeur)

## 🔗 Repository

[View on GitHub](https://github.com/OussemaBenAmeur/IRMusicSync)

## 🎵 Use Cases

- Home entertainment lighting
- Party atmosphere enhancement
- Music visualization
- Ambient lighting for music listening
- DIY smart home lighting projects

## ⚠️ Notes

- **IR Blaster Required**: This app requires a physical IR blaster on your Android device. Many modern phones no longer include this feature.
- **Compatibility**: Tested on devices with IR blasters. Compatibility may vary by device and LED controller model.
- **Audio Source**: Works best with music playback. Other audio sources may produce different results.

---

⭐ If you find this project useful, please consider giving it a star!

🎵 Enjoy synchronized lighting with your music!
