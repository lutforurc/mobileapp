# Android Studio build/run errors: what to hand to the coding agent

Three limits worth knowing before you ask for a fix. The wording below is quoted
as written; the "step 1 / step 3" references point at the surrounding reporting
workflow these notes were taken from, which is not reproduced here.

- **Include the error text.** In Android Studio, copy the red text from the Build
  tab (compile errors) or Logcat (crashes while the app runs) and paste it into
  step 3. Without it, the agent can only fix what step 1 finds.

- **"Something went wrong" is not a code error.** It comes from one of Android
  Studio's own panels, so it can't be fixed from the code.

- **The agent can't log in on a phone or emulator to check the screens.** After it
  fixes the build, install the app and check the screen yourself.
