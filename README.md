# NightRacing
NightRacing is a 2D endless racing game developed using Java and JavaFX. Dodge incoming traffic, collect coins, accelerate to higher speeds, and challenge yourself to beat your high score. Featuring animated road movement, particle effects, sound effects, and keyboard controls, the game offers an engaging arcade-style racing experience. 

# A simple top-down endless racing game made with JavaFX.

## Project structure

```text
Car Race/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── Main.java
```

## Requirements

- JDK 17
- Maven
- VS Code with Extension Pack for Java

This project uses JavaFX 21.0.4 through Maven.

## IMPORTANT: How to run

Open the VS Code terminal in the folder containing `pom.xml`.

Check Java:

```powershell
java -version
```

Check Maven:

```powershell
mvn -version
```

Then run:

```powershell
mvn clean javafx:run
```

# DO NOT use the normal Java "Run" button on `Main.java` for this project.

The Maven JavaFX plugin supplies the JavaFX runtime modules that the normal Java runner is missing.

## Controls

- ENTER / SPACE = Start
- LEFT / RIGHT = Steer
- A / D = Steer
- P = Pause/Resume
- ESC = Quit

## If Maven says it is not recognized

Install Apache Maven and make sure Maven is on PATH, then reopen VS Code.

Also make sure JAVA_HOME points to your JDK 17 installation.

Example:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
```

Then:

```powershell
mvn -version
```

and:

```powershell
mvn clean javafx:run
```
# Demo 
- ![alt text](<Demo of Night Racing.gif>)