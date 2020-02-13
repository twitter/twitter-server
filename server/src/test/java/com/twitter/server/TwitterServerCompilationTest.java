package com.twitter.server;

import java.util.Arrays;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.util.logging.Logger;

public class TwitterServerCompilationTest extends AbstractTwitterServer  {
  private static final Logger LOG = Logger.apply("TwitterServerCompilationTest");

  private Flag<String> foo =
    flag().create("foo", "default-foo", "help-foo", Flaggable.ofString());

  @Override
  public void onInit() {
    LOG.info("on-init");
  }

  @Override
  public void preMain() {
    LOG.info("pre-exit");
  }

  /**
   * This method runs automatically.
   */
  @Override
  public void main() throws Throwable {
    LOG.info("on-main for " + name());
    LOG.info("args:" + Arrays.toString(args()));

    // Make sure that public fields/methods from AbstractTwitterServer are available here
    statsReceiver();
    defaultAdminPort();
  }

  @Override
  public void postMain() {
    LOG.info("post-main");
  }

  @Override
  public void onExit() {
    LOG.info("on-exit");
  }

  @Override
  public void onExitLast() {
    LOG.info("on-exit-last");
  }
}
