samsung-health-data-api-1.0.0.aar is Samsung's Health Data SDK (version 1.0.0), which the face
companion's phone half uses to read Samsung Health. Samsung doesn't publish it to a package
repository; this copy is the one the open-source open_wearables_android_sdk project ships
(github.com/the-momentum/open_wearables_android_sdk, sdk/libs/maven/.../data-1.0.0.aar).

Samsung's own download (newer, 1.1.0 at the time of writing) is at
https://developer.samsung.com/health/data/overview.html (sign in with a Samsung account). To use
it, drop its samsung-health-data-api-<version>.aar in this folder: the build picks the newest
samsung-health-data-api*.aar here. Remove every .aar here to build without Samsung Health (the
health pages then show "--").

Samsung's SDK is under Samsung's own terms (see their site); it's here for building this project.
