# MQTT: Paho шукає мережеві модулі через ServiceLoader і рефлексію.
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Сканер штрих-кодів відкривається системою за назвою класу.
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.**

# Пакет застосунку видно в журналі падінь — імена рядків лишаємо, щоб помилки можна було зрозуміти.
-keepattributes SourceFile,LineNumberTable
