package com.kylinolap.metadata.realization;

import com.google.common.collect.*;
import com.kylinolap.common.KylinConfig;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.tools.jcore.ClassFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Hongbin Ma(Binmahone) on 12/18/14.
 */
public class RealizationRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(RealizationRegistry.class);
    private static final ConcurrentHashMap<KylinConfig, RealizationRegistry> CACHE = new ConcurrentHashMap<KylinConfig, RealizationRegistry>();

    public static RealizationRegistry getInstance(KylinConfig config) {
        RealizationRegistry r = CACHE.get(config);
        if (r != null) {
            return r;
        }

        synchronized (RealizationRegistry.class) {
            r = CACHE.get(config);
            if (r != null) {
                return r;
            }
            try {
                r = new RealizationRegistry(config);
                CACHE.put(config, r);
                return r;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to init CubeManager from " + config, e);
            }
        }
    }

    public static void removeInstance(KylinConfig config) {
        CACHE.remove(config);
    }

    // ============================================================================

    private Map<RealizationType, IRealizationProvider> providers;
    private KylinConfig config;

    private RealizationRegistry(KylinConfig config) throws IOException {
        logger.info("Initializing RealizationRegistry with metadata url " + config);
        this.config = config;
        init();
    }

    private void init() {
        providers = Maps.newConcurrentMap();
        
        // use reflection to load providers
        final Set<Class<? extends IRealizationProvider>> realizationProviders = new Reflections("").getSubTypesOf(IRealizationProvider.class);
        List<Throwable> es = Lists.newArrayList();
        for (Class<? extends IRealizationProvider> cls : realizationProviders) {
            try {
                IRealizationProvider p = (IRealizationProvider) cls.getMethod("getInstance", KylinConfig.class).invoke(null, config);
                providers.put(p.getRealizationType(), p);
                
            } catch (Exception | NoClassDefFoundError e) {
                es.add(e);
            }

            if (es.size() > 0) {
                for (Throwable exceptionOrError : es) {
                    logger.error("Create new store instance failed ", exceptionOrError);
                }
                throw new IllegalArgumentException("Failed to find metadata store by url: " + config.getMetadataUrl());
            }
        }
        
        logger.info("RealizationRegistry is " + providers);
    }

    public IRealization getRealization(RealizationType type, String name) {
        IRealizationProvider p = providers.get(type);
        if (p == null)
            throw new IllegalStateException("No provider for realization type " + type);
        
        return p.getRealization(name);
    }

}
