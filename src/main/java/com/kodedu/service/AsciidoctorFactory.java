package com.kodedu.service;

import com.kodedu.helper.IOHelper;
import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Component
public class AsciidoctorFactory {
    private static final Logger logger = LoggerFactory.getLogger(AsciidoctorFactory.class);
    private static final CountDownLatch plainDoctorReady = new CountDownLatch(1);
    private static final CountDownLatch revealDoctorReady = new CountDownLatch(1);
    private static final CountDownLatch htmlDoctorReady = new CountDownLatch(1);
    private static final CountDownLatch nonHtmlDoctorReady = new CountDownLatch(1);
    private static Asciidoctor plainDoctor;
    private static Asciidoctor revealDoctor;
    private static Asciidoctor htmlDoctor;
    private static Asciidoctor nonHtmlDoctor;
    private static DirectoryService directoryService;
    private static final Map<Asciidoctor, UserExtension> userExtensionMap = new ConcurrentHashMap<>();

    private static final BlockingQueue<Asciidoctor> blockingQueue = new LinkedBlockingQueue<>(4);

    @EventListener
    @Order(HIGHEST_PRECEDENCE)
    public void handleContextRefreshEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        Thread.startVirtualThread(() -> {
            initializeDoctors();
            Thread.startVirtualThread(() -> {
                directoryService = context.getBean(DirectoryService.class);
            });
            plainDoctor = context.getBean("plainDoctor", Asciidoctor.class);
            plainDoctorReady.countDown();
            htmlDoctor = context.getBean("htmlDoctor", Asciidoctor.class);
            htmlDoctorReady.countDown();
            nonHtmlDoctor = context.getBean("nonHtmlDoctor", Asciidoctor.class);
            nonHtmlDoctorReady.countDown();
            revealDoctor = context.getBean("revealDoctor", Asciidoctor.class);
            revealDoctorReady.countDown();
        });
    }

    private static void checkUserExtensions(Asciidoctor doctor) {
        if (Objects.isNull(directoryService)) {
            return;
        }
        Path workingDir = directoryService.workingDirectory();
        Path libDir = workingDir.resolve(".asciidoctor/lib");
        if (Files.notExists(libDir)) {
            UserExtension userExtension = userExtensionMap.get(doctor);
            if (Objects.nonNull(userExtension)) {
                userExtension.registerExtensions(doctor, new ArrayList<>());
            }
            return;
        }

        List<Path> extensions = IOHelper.walk(libDir, 2)
                .filter(p -> p.toString().endsWith(".rb") || p.toString().endsWith(".jar"))
                .sorted().toList();

        UserExtension userExtension = userExtensionMap.compute(doctor, (adoc, uEx) -> {
            if (Objects.nonNull(uEx)) {
                return uEx;
            }
            UserExtension extension = new UserExtension();
            extension.setExtensionGroup(adoc.createGroup());
            return extension;
        });
        userExtension.registerExtensions(doctor, extensions);
    }

    public static Asciidoctor getHtmlDoctor() {
        waitLatch(htmlDoctorReady);
        checkUserExtensions(htmlDoctor);
        return htmlDoctor;
    }

    public static Asciidoctor getNonHtmlDoctor() {
        waitLatch(nonHtmlDoctorReady);
        checkUserExtensions(nonHtmlDoctor);
        return nonHtmlDoctor;
    }

    public static Asciidoctor getPlainDoctor() {
        waitLatch(plainDoctorReady);
        checkUserExtensions(plainDoctor);
        return plainDoctor;
    }

    public static Asciidoctor getRevealDoctor() {
        waitLatch(revealDoctorReady);
        checkUserExtensions(revealDoctor);
        return revealDoctor;
    }

    public void initializeDoctors() {
        Thread.startVirtualThread(() -> {
            IntStream.rangeClosed(1, 4)
                    .forEach(AsciidoctorFactory::initializeDoctor);
        });
    }

    private static void initializeDoctor(int retry) {
        try {
            Asciidoctor asciidoctor = Asciidoctor.Factory.create();
            blockingQueue.add(asciidoctor);
        }catch (Throwable t){
            logger.error("Problem occurred while initializing asciidoctor retry {}",retry, t);
        }
    }

    public static Asciidoctor getAsciidoctor() {
        Asciidoctor doctor;
        try {
            doctor = blockingQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return doctor;
    }

    private static void waitLatch(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
