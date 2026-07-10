package org.ironsight.cubearray.platform;

/**
 * Application-wide constants that infrastructure code needs but that must not create a dependency on
 * the app entry point. Keeping {@code APP_NAME} here (rather than on {@code CubeArrayMain}) lets
 * {@link ResourceUtils} resolve the install directory without the {@code platform} package reaching
 * up into the root application class.
 */
public final class AppInfo {
  public static final String APP_NAME = "CubeArray";

  private AppInfo() {}
}
