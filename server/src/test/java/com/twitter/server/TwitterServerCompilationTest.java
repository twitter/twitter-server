package com.twitter.server;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;

public class TwitterServerCompilationTest extends AbstractTwitterServer  {

  private Flag<String> foo =
    flag().create("foo", "default-foo", "help-foo", Flaggable.ofString());

  @Override
  public void preMain() {
    log().info("pre-exit");
  }

  /**
   * This method runs automatically.
   */
  @Override
  public void main() throws Throwable {
    log().info("on-main for " + name());
    log().info("args:" + args());

    // Make sure that public fields/methods from AbstractTwitterServer are available here
    statsReceiver();
    defaultAdminPort();
  }

  @Override
  public void postMain() {
    log().info("post-main");
  }

  @Override
  public void onInit() {
    log().info("on-init");
  }

  @Override
  public void onExit() {
    log().info("on-exit");
  }
}
