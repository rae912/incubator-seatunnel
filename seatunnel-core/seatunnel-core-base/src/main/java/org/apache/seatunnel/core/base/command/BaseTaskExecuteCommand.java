/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.core.base.command;

import org.apache.seatunnel.apis.base.env.RuntimeEnv;
import org.apache.seatunnel.apis.base.plugin.Plugin;
import org.apache.seatunnel.apis.base.plugin.PluginClosedException;
import org.apache.seatunnel.common.Constants;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.config.Common;
import org.apache.seatunnel.common.config.DeployMode;
import org.apache.seatunnel.core.base.utils.CompressionUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Base task execute command.
 *
 * @param <T> command args.
 */
@Slf4j
public abstract class BaseTaskExecuteCommand<T extends AbstractCommandArgs, E extends RuntimeEnv> implements Command<T> {

    /**
     * Check the plugin config.
     *
     * @param plugins plugin list.
     */
    @SafeVarargs
    protected final void baseCheckConfig(List<? extends Plugin<E>>... plugins) {
        pluginCheck(plugins);
        deployModeCheck();
    }

    /**
     * Execute prepare method defined in {@link Plugin}.
     *
     * @param env     runtimeEnv
     * @param plugins plugin list
     */
    @SafeVarargs
    protected final void prepare(E env, List<? extends Plugin<E>>... plugins) {
        for (List<? extends Plugin<E>> pluginList : plugins) {
            pluginList.forEach(plugin -> plugin.prepare(env));
        }
    }

    /**
     * Execute close method defined in {@link Plugin}
     *
     * @param plugins plugin list
     */
    @SafeVarargs
    protected final void close(List<? extends Plugin<E>>... plugins) {
        PluginClosedException exceptionHolder = null;
        for (List<? extends Plugin<E>> pluginList : plugins) {
            for (Plugin<E> plugin : pluginList) {
                try (Plugin<?> closed = plugin) {
                    // ignore
                } catch (Exception e) {
                    exceptionHolder = exceptionHolder == null ?
                            new PluginClosedException("below plugins closed error:") : exceptionHolder;
                    exceptionHolder.addSuppressed(new PluginClosedException(
                            String.format("plugin %s closed error", plugin.getClass()), e));
                }
            }
        }
        if (exceptionHolder != null) {
            throw exceptionHolder;
        }
    }

    /**
     * Print the logo.
     */
    protected void showAsciiLogo() {
        String printAsciiLogo = System.getenv("SEATUNNEL_PRINT_ASCII_LOGO");
        if ("true".equalsIgnoreCase(printAsciiLogo)) {
            log.info('\n' + Constants.ST_LOGO);
            log.info(Constants.COPYRIGHT_LINE);
        }
    }

    /**
     * Execute the checkConfig method defined in {@link Plugin}.
     *
     * @param plugins plugin list
     */
    private void pluginCheck(List<? extends Plugin<E>>... plugins) {
        for (List<? extends Plugin<E>> pluginList : plugins) {
            for (Plugin<E> plugin : pluginList) {
                CheckResult checkResult;
                try {
                    checkResult = plugin.checkConfig();
                } catch (Exception e) {
                    checkResult = CheckResult.error(e.getMessage());
                }
                if (!checkResult.isSuccess()) {
                    log.error("Plugin[{}] contains invalid config, error: {} \n", plugin.getClass().getName(), checkResult.getMsg());
                    System.exit(-1); // invalid configuration
                }
            }
        }
    }

    private void deployModeCheck() {
        final DeployMode mode = Common.getDeployMode();
        if (DeployMode.CLUSTER == mode) {

            log.info("preparing cluster mode work dir files...");
            File workDir = new File(".");

            for (File file : Objects.requireNonNull(workDir.listFiles())) {
                log.warn("\t list file: {} ", file.getAbsolutePath());
            }
            // decompress plugin dir
            File compressedFile = new File("plugins.tar.gz");

            try {
                File tempFile = CompressionUtils.unGzip(compressedFile, workDir);
                CompressionUtils.unTar(tempFile, workDir);
            } catch (Exception e) {
                log.error("failed to decompress plugins.tar.gz", e);
                System.exit(-1);
            }
            log.info("succeeded to decompress plugins.tar.gz");
        }
    }
}
