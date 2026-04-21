ANDROID_HOME ?= /opt/homebrew/share/android-commandlinetools
export ANDROID_HOME
export ANDROID_SDK_ROOT := $(ANDROID_HOME)

GRADLE ?= ./gradlew
APP_ID  := com.soorkie.adblockvpn
ACTIVITY := $(APP_ID)/.MainActivity
ADB ?= $(ANDROID_HOME)/platform-tools/adb

DEBUG_APK   := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release-unsigned.apk

.PHONY: help wrapper build release run install install-release uninstall clean logcat stop

help:
	@echo "Targets:"
	@echo "  make wrapper         - generate Gradle wrapper (one-time)"
	@echo "  make build           - assemble debug APK -> $(DEBUG_APK)"
	@echo "  make release         - assemble unsigned release APK -> $(RELEASE_APK)"
	@echo "  make install         - install debug APK on connected device"
	@echo "  make run             - install + launch on connected device"
	@echo "  make uninstall       - uninstall app from device"
	@echo "  make logcat          - tail logs filtered to this app"
	@echo "  make stop            - force-stop the app on device"
	@echo "  make clean           - gradle clean"

wrapper:
	gradle wrapper

build:
	$(GRADLE) :app:assembleDebug

release:
	$(GRADLE) :app:assembleRelease
	@echo "Unsigned release APK: $(RELEASE_APK)"

install: build
	$(ADB) install -r -t "$(DEBUG_APK)"

install-release: release
	$(ADB) install -r "$(RELEASE_APK)"

run: install
	$(ADB) shell am start -n $(ACTIVITY)

uninstall:
	-$(ADB) uninstall $(APP_ID)

stop:
	-$(ADB) shell am force-stop $(APP_ID)

logcat:
	$(ADB) logcat -v time LocalVpnService:V AndroidRuntime:E *:S

clean:
	$(GRADLE) clean
