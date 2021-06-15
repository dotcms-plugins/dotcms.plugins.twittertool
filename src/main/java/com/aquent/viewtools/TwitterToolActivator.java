package com.aquent.viewtools;

import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import org.apache.commons.io.IOUtils;
import org.osgi.framework.BundleContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class TwitterToolActivator extends GenericBundleActivator {

    @Override
    public void start ( BundleContext bundleContext ) throws Exception {

        //Initializing services...
        initializeServices( bundleContext );

        //Registering the ViewTool service
        registerViewToolService( bundleContext, new TwitterToolInfo() );

        // copy the yaml
        copyAppYml();
    }

    @Override
    public void stop ( BundleContext bundleContext ) throws Exception {
        unregisterViewToolServices();
        deleteYml();
    }

    private final File installedAppYaml = new File(ConfigUtils.getAbsoluteAssetsRootPath() + File.separator + "server"
            + File.separator + "apps" + File.separator + AppKeys.APP_YAML_NAME);

    /**
     * copies the App yaml to the apps directory and refreshes the apps
     *
     * @throws java.io.IOException
     */
    private void copyAppYml() throws IOException {

        Logger.info(this.getClass().getName(), "copying YAML File:" + installedAppYaml);
        try (final InputStream in = this.getClass().getResourceAsStream("/" + AppKeys.APP_YAML_NAME)) {
            IOUtils.copy(in, Files.newOutputStream(installedAppYaml.toPath()));
        }
        CacheLocator.getAppsCache().clearCache();
    }

    /**
     * Deletes the App yaml to the apps directory and refreshes the apps
     *
     * @throws IOException
     */
    private void deleteYml() throws IOException {

        Logger.info(this.getClass().getName(), "deleting the YAML File:" + installedAppYaml);

        installedAppYaml.delete();
        CacheLocator.getAppsCache().clearCache();
    }
}
