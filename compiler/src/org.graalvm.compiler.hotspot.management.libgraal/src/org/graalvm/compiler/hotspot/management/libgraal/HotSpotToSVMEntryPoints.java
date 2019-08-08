/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.management.libgraal;

import static org.graalvm.libgraal.jni.JNIUtil.GetStaticMethodID;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;
import org.graalvm.libgraal.OptionsEncoder;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNI.JMethodID;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.WordFactory;

public final class HotSpotToSVMEntryPoints {

    private static final String HS_BEAN_CLASS_NAME = null;
    private static final byte[] HS_BEAN_CLASS = null;
    private static final String HS_BEAN_FACTORY_CLASS_NAME = null;
    private static final byte[] HS_BEAN_FACTORY_CLASS = null;
    private static final String HS_SVM_CALLS_CLASS_NAME = null;
    private static final byte[] HS_SVM_CALLS_CLASS = null;
    private static final String HS_PUSHBACK_ITER_CLASS_NAME = null;
    private static final byte[] HS_PUSHBACK_ITER_CLASS = null;

    private HotSpotToSVMEntryPoints() {
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_truffle_runtime_hotspot_libgraal_JMXInitializer_init")
    public static void init(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JNI.JObject classLoader) {
        if (defineClassInHotSpot(env, classLoader, HS_PUSHBACK_ITER_CLASS_NAME, HS_PUSHBACK_ITER_CLASS).isNull()) {
            throw new InternalError("Failed to define HotSpotToSVMCalls class.");
        }

        if (defineClassInHotSpot(env, classLoader, HS_SVM_CALLS_CLASS_NAME, HS_SVM_CALLS_CLASS).isNull()) {
            throw new InternalError("Failed to define HotSpotToSVMCalls class.");
        }

        if (defineClassInHotSpot(env, classLoader, HS_BEAN_CLASS_NAME, HS_BEAN_CLASS).isNull()) {
            throw new InternalError("Failed to define MXBean class.");
        }
        JNI.JClass factoryClass = defineClassInHotSpot(env, classLoader, HS_BEAN_FACTORY_CLASS_NAME, HS_BEAN_FACTORY_CLASS);
        if (factoryClass.isNull()) {
            throw new InternalError("Failed to define Factory class.");
        }
        JMethodID createId;
        try (CCharPointerHolder name = toCString("create"); CCharPointerHolder sig = toCString("()Lorg/graalvm/compiler/hotspot/management/libgraal/runtime/Factory;")) {
            createId = GetStaticMethodID(env, factoryClass, name.get(), sig.get());
            if (createId.isNull()) {
                throw new InternalError("No such method: create");
            }
        }
        JNI.JObject result = env.getFunctions().getCallStaticObjectMethodA().call(env, factoryClass, createId, WordFactory.nullPointer());
        if (result.isNull()) {
            throw new InternalError("Failed to initiate Factory.");
        }
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_pollRegistrations")
    public static JNI.JLongArray pollRegistrations(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        List<HotSpotGraalManagement> registrations = HotSpotGraalManagement.Factory.drain();
        JNI.JLongArray res = JNIUtil.NewLongArray(env, registrations.size());
        CLongPointer elems = JNIUtil.GetLongArrayElements(env, res, WordFactory.nullPointer());
        try {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            for (int i = 0; i < registrations.size(); i++) {
                long handle = globalHandles.create(registrations.get(i)).rawValue();
                elems.write(i, handle);
            }
        } finally {
            JNIUtil.ReleaseLongArrayElements(env, res, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        return res;
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_finishRegistration")
    public static void finishRegistration(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JNI.JLongArray svmRegistrations) {
        long len = JNIUtil.GetArrayLength(env, svmRegistrations);
        CLongPointer elems = JNIUtil.GetLongArrayElements(env, svmRegistrations, WordFactory.nullPointer());
        try {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            for (int i = 0; i < len; i++) {
                HotSpotGraalManagement registration = globalHandles.get(WordFactory.pointer(elems.read(i)));
                registration.finishRegistration();
            }
        } finally {
            JNIUtil.ReleaseLongArrayElements(env, svmRegistrations, elems, JNI.JArray.MODE_RELEASE);
        }
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_getRegistrationName")
    public static JNI.JString getRegistrationName(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration) {
        ObjectHandles globalHandles = ObjectHandles.getGlobal();
        HotSpotGraalManagement registration = globalHandles.get(WordFactory.pointer(svmRegistration));
        String name = registration.getName();
        return JNIUtil.createHSString(env, name);
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_getMBeanInfo")
    public static JNI.JByteArray getMBeanInfo(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration) {
        ObjectHandles globalHandles = ObjectHandles.getGlobal();
        HotSpotGraalManagement registration = globalHandles.get(WordFactory.pointer(svmRegistration));
        MBeanInfo info = registration.getBean().getMBeanInfo();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bean.class", info.getClassName());
        map.put("bean.description", info.getDescription());
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            String name = attr.getName();
            String type = attr.getType();
            String description = attr.getDescription();
            boolean isReadable = attr.isReadable();
            boolean isWritable = attr.isWritable();
            boolean isIs = attr.isIs();
            map.put("attr." + name + ".name", name);
            map.put("attr." + name + ".type", type);
            map.put("attr." + name + ".description", description);
            map.put("attr." + name + ".r", isReadable);
            map.put("attr." + name + ".w", isWritable);
            map.put("attr." + name + ".i", isIs);
        }
        byte[] serialized = OptionsEncoder.encode(map);
        JNI.JByteArray res = JNIUtil.NewByteArray(env, serialized.length);
        CCharPointer elems = JNIUtil.GetByteArrayElements(env, res, WordFactory.nullPointer());
        try {
            CTypeConversion.asByteBuffer(elems, serialized.length).put(serialized);
        } finally {
            JNIUtil.ReleaseByteArrayElements(env, res, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        return res;
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_getAttributes")
    public static JNI.JByteArray getAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration, JNI.JObjectArray requiredAttributes) {
        int len = JNIUtil.GetArrayLength(env, requiredAttributes);
        String[] attrNames = new String[len];
        for (int i = 0; i < len; i++) {
            JNI.JString el = (JNI.JString) JNIUtil.GetObjectArrayElement(env, requiredAttributes, i);
            attrNames[i] = JNIUtil.createString(env, el);
        }
        HotSpotGraalManagement registration = ObjectHandles.getGlobal().get(WordFactory.pointer(svmRegistration));
        AttributeList attributesList = registration.getBean().getAttributes(attrNames);
        return attributeListToRaw(env, attributesList);
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_libgraal_runtime_HotSpotToSVMCalls_setAttributes")
    public static JNI.JByteArray setAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration, JNI.JByteArray attributes) {
        int len = JNIUtil.GetArrayLength(env, attributes);
        byte[] serialized = new byte[len];
        CCharPointer elems = JNIUtil.GetByteArrayElements(env, attributes, WordFactory.nullPointer());
        try {
            CTypeConversion.asByteBuffer(elems, len).get(serialized);
        } finally {
            JNIUtil.ReleaseByteArrayElements(env, attributes, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        Map<String, Object> map = OptionsEncoder.decode(serialized);
        AttributeList attributesList = new AttributeList();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            attributesList.add(new Attribute(entry.getKey(), entry.getValue()));
        }
        HotSpotGraalManagement registration = ObjectHandles.getGlobal().get(WordFactory.pointer(svmRegistration));
        attributesList = registration.getBean().setAttributes(attributesList);
        return attributeListToRaw(env, attributesList);
    }

    private static JNI.JByteArray attributeListToRaw(JNI.JNIEnv env, AttributeList attributesList) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Object item : attributesList) {
            Attribute attr = (Attribute) item;
            values.put(attr.getName(), attr.getValue());
        }
        byte[] serialized = OptionsEncoder.encode(values);
        JNI.JByteArray res = JNIUtil.NewByteArray(env, serialized.length);
        CCharPointer elems = JNIUtil.GetByteArrayElements(env, res, WordFactory.nullPointer());
        try {
            CTypeConversion.asByteBuffer(elems, serialized.length).put(serialized);
        } finally {
            JNIUtil.ReleaseByteArrayElements(env, res, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        return res;
    }

    private static JNI.JClass defineClassInHotSpot(JNI.JNIEnv env, JNI.JObject classLoader, String clazzName, byte[] clazz) {
        CCharPointer classData = UnmanagedMemory.malloc(clazz.length);
        ByteBuffer buffer = CTypeConversion.asByteBuffer(classData, clazz.length);
        buffer.put(clazz);
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(clazzName)) {
            return JNIUtil.DefineClass(
                            env,
                            className.get(),
                            classLoader,
                            classData,
                            clazz.length);
        } finally {
            UnmanagedMemory.free(classData);
        }
    }
}
