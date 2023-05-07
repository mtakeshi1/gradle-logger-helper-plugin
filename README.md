# gradle-logger-helper-plugin

Plugin for graddle to ease the life of programmers using SLF4J or Commons Logging

This plugin should do two things:

- wrap calls like

```java
LOGGER.debug(someMessage);
``` 

into

```java
LOGGER.isDebugEnabled()LOGGER.debug(someMessage);
```

- modify string constants in the log messages, replaing special
  strings ```{packageName}```, ```{className}```, ```{methodName}```, ```{lineNumber}``` to their corresponding values


