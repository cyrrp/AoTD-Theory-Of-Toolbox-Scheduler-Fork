package data.kaysaar.aotd.tot.plugins;

import ashlib.data.plugins.reflection.ReflectionBetterUtilis;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Pair;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtilis {
    // Code taken and modified from Grand Colonies
    private static final Class<?> fieldClass;
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final MethodHandle setFieldHandle;
    private static final MethodHandle getFieldHandle;
    private static final MethodHandle getFieldNameHandle;
    private static final MethodHandle setFieldAccessibleHandle;
    private static final Class<?> methodClass;
    private static final Class<?> constructorClass;
    private static final MethodHandle getMethodNameHandle;
    private static final MethodHandle invokeMethodHandle;
    private static final MethodHandle setMethodAccessable;
    private static final MethodHandle getModifiersHandle;
    private static final MethodHandle getParameterTypesHandle;
    private static final MethodHandle isMethodVarArgsHandle;
    private static final MethodHandle getFieldTypeHandle;
    private static final MethodHandle getDeclaredConstructorsHandle;
    private static final Class<?> fileclass;
    private static final Class<?> fileWriterClass;
    private static final Class<?> fileReaderClass;
    private static final Class<?> bufferedReaderClass;
    private static final Class<?> readerClass;
    private static final MethodHandle fileGetParentFileHandle;
    private static final MethodHandle fileGetNameHandle;
    private static final MethodHandle fileCtorParentChildHandle;
    private static final MethodHandle fileRenameToHandle;
    private static final MethodHandle fileGetCanonicalPathHandle;

    static {
        try {
            fieldClass =
                    Class.forName("java.lang.reflect.Field", false, Class.class.getClassLoader());
            setFieldHandle =
                    lookup.findVirtual(
                            fieldClass,
                            "set",
                            MethodType.methodType(Void.TYPE, Object.class, Object.class));
            getFieldHandle =
                    lookup.findVirtual(
                            fieldClass, "get", MethodType.methodType(Object.class, Object.class));
            getFieldNameHandle =
                    lookup.findVirtual(fieldClass, "getName", MethodType.methodType(String.class));
            getFieldTypeHandle =
                    lookup.findVirtual(fieldClass, "getType", MethodType.methodType(Class.class));
            setFieldAccessibleHandle =
                    lookup.findVirtual(
                            fieldClass,
                            "setAccessible",
                            MethodType.methodType(Void.TYPE, boolean.class));

            methodClass =
                    Class.forName("java.lang.reflect.Method", false, Class.class.getClassLoader());
            getMethodNameHandle =
                    lookup.findVirtual(methodClass, "getName", MethodType.methodType(String.class));
            invokeMethodHandle =
                    lookup.findVirtual(
                            methodClass,
                            "invoke",
                            MethodType.methodType(Object.class, Object.class, Object[].class));
            setMethodAccessable =
                    lookup.findVirtual(
                            methodClass,
                            "setAccessible",
                            MethodType.methodType(Void.TYPE, boolean.class));
            getModifiersHandle =
                    lookup.findVirtual(
                            methodClass, "getModifiers", MethodType.methodType(int.class));
            getParameterTypesHandle =
                    lookup.findVirtual(
                            methodClass, "getParameterTypes", MethodType.methodType(Class[].class));
            isMethodVarArgsHandle =
                    lookup.findVirtual(
                            methodClass, "isVarArgs", MethodType.methodType(boolean.class));

            constructorClass =
                    Class.forName(
                            "java.lang.reflect.Constructor", false, Class.class.getClassLoader());
            getDeclaredConstructorsHandle =
                    lookup.findVirtual(
                            constructorClass,
                            "getParameterTypes",
                            MethodType.methodType(Class[].class));
            fileclass = Class.forName("java.io.File", false, Class.class.getClassLoader());
            fileWriterClass =
                    Class.forName("java.io.FileWriter", false, Class.class.getClassLoader());
            fileReaderClass =
                    Class.forName("java.io.FileReader", false, Class.class.getClassLoader());
            bufferedReaderClass =
                    Class.forName("java.io.BufferedReader", false, Class.class.getClassLoader());
            readerClass = Class.forName("java.io.Reader", false, Class.class.getClassLoader());
            fileGetParentFileHandle =
                    lookup.findVirtual(
                            fileclass, "getParentFile", MethodType.methodType(fileclass));
            fileGetNameHandle =
                    lookup.findVirtual(fileclass, "getName", MethodType.methodType(String.class));
            fileCtorParentChildHandle =
                    lookup.findConstructor(
                            fileclass, MethodType.methodType(Void.TYPE, fileclass, String.class));
            fileRenameToHandle =
                    lookup.findVirtual(
                            fileclass, "renameTo", MethodType.methodType(boolean.class, fileclass));
            fileGetCanonicalPathHandle =
                    lookup.findVirtual(
                            fileclass, "getCanonicalPath", MethodType.methodType(String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object findFieldWithMethodName(Object instance, String methodName) {
        try {
            Class<?> currentClass = instance.getClass();
            Object[] fields = currentClass.getDeclaredFields();

            for (Object field : fields) {
                try {
                    setFieldAccessibleHandle.invoke(field, true);
                    Object fieldValue = getFieldHandle.invoke(field, instance);
                    if (fieldValue == null) continue;
                    if (hasMethodOfName(methodName, fieldValue)) {
                        return fieldValue;
                    }
                } catch (Throwable inner) {
                    inner.printStackTrace();
                }
            }

            return null;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to find field with method name: " + methodName, e);
        }
    }

    public static String getCanonicalPath(Object fileObj) {
        try {
            return (String) fileGetCanonicalPathHandle.invoke(fileObj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean moveFileOneLevelUp(String absolutePath) {
        try {
            // src: /mods/yourMod/graphics/cursors/stuff/foo.png
            Object src = getFile(absolutePath);

            // parent: /mods/yourMod/graphics/cursors/stuff
            Object parent = fileGetParentFileHandle.invoke(src);
            if (parent == null) return false;

            // grandParent: /mods/yourMod/graphics/cursors
            Object grandParent = fileGetParentFileHandle.invoke(parent);
            if (grandParent == null) return false;

            // name: "foo.png"
            String name = (String) fileGetNameHandle.invoke(src);

            // dest: /mods/yourMod/graphics/cursors/foo.png
            Object dest = fileCtorParentChildHandle.invoke(grandParent, name);

            // renameTo(dest)
            return (boolean) fileRenameToHandle.invoke(src, dest);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean moveFileOneLevelUpInModGraphics(String modId, String absolutePath) {
        try {
            String modBase =
                    Global.getSettings()
                            .getModManager()
                            .getModSpec(modId)
                            .getPath(); // e.g. .../mods/YourMod/
            modBase = modBase.replace("\\", "/");
            Object modGraphicsFile = ReflectionUtilis.getFile(modBase + "/graphics");
            String modGraphicsCanonical = ReflectionUtilis.getCanonicalPath(modGraphicsFile);

            Object fileObj = ReflectionUtilis.getFile(absolutePath);
            String fileCanonical = ReflectionUtilis.getCanonicalPath(fileObj);

            // safety: ensure it's under /mods/YourMod/graphics
            if (!fileCanonical.startsWith(modGraphicsCanonical)) {
                // not our file, do nothing
                return false;
            }

            return ReflectionUtilis.moveFileOneLevelUp(fileCanonical);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object instantiateExact(
            Class<?> clazz, Class<?>[] parameterTypes, Object... arguments) {
        try {
            // Match constructor exactly with the provided parameter types
            MethodType ctorType = MethodType.methodType(void.class, parameterTypes);
            MethodHandle constructorHandle = lookup.findConstructor(clazz, ctorType);

            // Invoke the constructor with the provided arguments
            return constructorHandle.invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to instantiate (exact) " + clazz.getName(), e);
        }
    }

    public static ButtonAPI findButtonWithText(
            Object instance, String textQuery, boolean caseInsensitive, boolean substringMatch) {
        if (instance == null || textQuery == null) return null;

        try {
            Class<?> current = instance.getClass();
            while (current != null) {
                Object[] fields = current.getDeclaredFields();
                for (Object field : fields) {
                    try {
                        // Make field accessible and get its value
                        setFieldAccessibleHandle.invoke(field, true);
                        Object value = getFieldHandle.invoke(field, instance);
                        if (!(value instanceof ButtonAPI)) continue;

                        String text = ((ButtonAPI) value).getText();
                        if (text == null) continue;

                        String a = caseInsensitive ? text.toLowerCase() : text;
                        String b = caseInsensitive ? textQuery.toLowerCase() : textQuery;

                        boolean match = substringMatch ? a.contains(b) : a.equals(b);
                        if (match) return (ButtonAPI) value;
                    } catch (Throwable inner) {
                        // Mirror your style: don't abort on a single bad field
                        inner.printStackTrace();
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to find ButtonAPI with text \"" + textQuery + "\"", e);
        }
    }

    public static boolean doesHaveConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            if (parameterTypes == null) parameterTypes = new Class<?>[0];

            Class<?>[] normalized = new Class<?>[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> p = parameterTypes[i];
                normalized[i] =
                        (p != null && getPrimitiveType(p) != null) ? getPrimitiveType(p) : p;
            }

            MethodType ctorType = MethodType.methodType(void.class, normalized);
            lookup.findConstructor(clazz, ctorType);
            return true; // found & accessible
        } catch (Throwable t) {
            return false; // not found or not accessible
        }
    }

    public static boolean doesHaveConstructor(Class<?> clazz, Object... arguments) {
        if (arguments == null) arguments = new Object[0];

        // Null args are ambiguous for an "exact" signature check.
        for (Object arg : arguments) {
            if (arg == null) return false;
        }

        try {
            Class<?>[] parameterTypes = new Class<?>[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Class<?> c = arguments[i].getClass();
                Class<?> prim = getPrimitiveType(c);
                parameterTypes[i] = (prim != null) ? prim : c;
            }

            MethodType ctorType = MethodType.methodType(void.class, parameterTypes);
            lookup.findConstructor(clazz, ctorType);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static Object instantiateAutoProjected(Class<?> targetClass, Object... arguments) {
        try {
            if (arguments == null) arguments = new Object[0];

            // Get ALL declared ctors via indirection (no direct Constructor usage)
            Object[] ctors =
                    (Object[])
                            invokeMethodWithAutoProjection("getDeclaredConstructors", targetClass);
            if (ctors == null || ctors.length == 0) {
                throw new NoSuchMethodException("No constructors on " + targetClass.getName());
            }

            for (Object ctor : ctors) {
                try {
                    // Parameter types of this ctor
                    Class<?>[] paramTypes = (Class<?>[]) getDeclaredConstructorsHandle.invoke(ctor);
                    boolean varArgs = false;
                    try {
                        varArgs = (boolean) invokeMethodWithAutoProjection("isVarArgs", ctor);
                    } catch (Throwable ignored) {
                    }

                    // Quick arity checks
                    if (!varArgs && paramTypes.length != arguments.length) continue;
                    if (varArgs && arguments.length < paramTypes.length - 1) continue;

                    // Try to project/convert arguments for this ctor shape
                    Object[] projected = projectCtorArgs(arguments, paramTypes, varArgs);
                    if (projected == null) continue; // couldn't convert for this ctor

                    // Be permissive with access like elsewhere
                    try {
                        invokeMethodWithAutoProjection("setAccessible", ctor, true);
                    } catch (Throwable ignored) {
                    }

                    // NOTE: Constructor::newInstance takes a single Object[] parameter
                    return invokeMethodWithAutoProjection("newInstance", ctor, (Object) projected);
                } catch (Throwable perCtor) {
                    // Try other ctors; mirror your style (don't fail the loop on one bad attempt)
                    perCtor.printStackTrace();
                }
            }

            throw new NoSuchMethodException(
                    "No compatible constructor on "
                            + targetClass.getName()
                            + " for "
                            + arguments.length
                            + " args after auto-projection");
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to instantiate (auto-projected) " + targetClass.getName(), e);
        }
    }

    /** Build the argument array for a constructor (handles fixed + varargs). */
    private static Object[] projectCtorArgs(Object[] args, Class<?>[] paramTypes, boolean varArgs) {
        try {
            if (!varArgs) {
                Object[] out = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    out[i] = convertArgumentAuto(args[i], paramTypes[i]);
                }
                return out;
            }

            // varargs: last param is T[]
            int fixedCount = paramTypes.length - 1;
            Class<?> arrayType = paramTypes[fixedCount];
            Class<?> compType = arrayType.getComponentType();

            // Fixed portion
            Object[] fixed = new Object[fixedCount];
            for (int i = 0; i < fixedCount; i++) {
                fixed[i] = convertArgumentAuto(args[i], paramTypes[i]);
            }

            int varCount = args.length - fixedCount;

            // If caller passed the varargs array already (exact type), just use it
            if (varCount == 1
                    && args[fixedCount] != null
                    && arrayType.isInstance(args[fixedCount])) {
                Object[] out = new Object[paramTypes.length];
                System.arraycopy(fixed, 0, out, 0, fixedCount);
                out[fixedCount] = args[fixedCount];
                return out;
            }

            // Build T[] and fill
            Object varArray = java.lang.reflect.Array.newInstance(compType, varCount);
            for (int i = 0; i < varCount; i++) {
                Object converted = convertArgumentAuto(args[fixedCount + i], compType);
                java.lang.reflect.Array.set(varArray, i, converted);
            }

            Object[] out = new Object[paramTypes.length];
            System.arraycopy(fixed, 0, out, 0, fixedCount);
            out[fixedCount] = varArray;
            return out;
        } catch (Throwable t) {
            // Signal this ctor doesn't fit
            return null;
        }
    }

    /**
     * Wrapper around your convertArgument(...) with a few safe, common widenings: - to String /
     * CharSequence via String.valueOf(...) - booleans & chars from String - enums from String
     * (name, case-insensitive fallback) or Number (ordinal) - java.io.File from String (constructed
     * via MethodHandles, not "new") - falls back to your convertArgument(...) for
     * primitives/boxing/casts
     */
    public static Object convertArgumentAuto(Object arg, Class<?> targetType) {
        // Null handling
        if (arg == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("null for primitive " + targetType.getName());
            }
            return null;
        }

        // Already assignable
        if (targetType.isAssignableFrom(arg.getClass())) return arg;

        // Strings / CharSequence
        if (targetType == String.class || CharSequence.class.isAssignableFrom(targetType)) {
            return String.valueOf(arg);
        }

        // Enums
        if (targetType.isEnum()) {
            return toEnumCoerce((Class<? extends Enum<?>>) targetType, arg);
        }

        // File from String (constructed via our MethodHandles path, no direct "new File")
        try {
            if (fileclass != null
                    && fileclass.isAssignableFrom(targetType)
                    && arg instanceof CharSequence) {
                return instantiateExact(fileclass, new Class<?>[] {String.class}, arg.toString());
            }
        } catch (Throwable ignored) {
        }

        // Booleans/Chars from String
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (arg instanceof CharSequence) return Boolean.parseBoolean(arg.toString().trim());
        }
        if (targetType == char.class || targetType == Character.class) {
            if (arg instanceof CharSequence) {
                String s = arg.toString();
                if (s.length() == 1) return s.charAt(0);
            }
        }

        // Let your original converter handle numbers, primitives, and normal casts
        return convertArgument(arg, targetType);
    }

    public static Object getFile(String pathAbsolute) {
        try {
            MethodHandle mh =
                    lookup.findConstructor(
                            fileclass, MethodType.methodType(Void.TYPE, String.class));
            Object fileObj = mh.invoke(pathAbsolute);
            return fileObj;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getFileWriter(String pathAbsolute, boolean append) {
        try {
            MethodHandle mh =
                    lookup.findConstructor(
                            fileWriterClass,
                            MethodType.methodType(Void.TYPE, String.class, boolean.class));
            Object fileObj = mh.invoke(pathAbsolute, append);
            return fileObj;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getFileReader(String pathAbsolute) {
        try {
            MethodHandle ctor =
                    lookup.findConstructor(
                            fileReaderClass, MethodType.methodType(Void.TYPE, String.class));
            return ctor.invoke(pathAbsolute);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getBufferedReader(Object reader) {
        try {
            MethodHandle ctor =
                    lookup.findConstructor(
                            bufferedReaderClass, MethodType.methodType(Void.TYPE, readerClass));
            return ctor.invoke(reader);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Generic close() for any Closeable/Reader/Writer
    public static void closeQuiet(Object closeable) {
        try {
            invokeMethodWithAutoProjection("close", closeable);
        } catch (Throwable ignored) {
        }
    }

    /** Enum coercion helpers – no direct reflection; uses standard Enum APIs. */
    private static Object toEnumCoerce(Class<? extends Enum<?>> enumType, Object arg) {
        if (enumType.isInstance(arg)) return arg;

        if (arg instanceof CharSequence) {
            String name = arg.toString();
            try {
                // exact first
                return Enum.valueOf((Class) enumType, name);
            } catch (IllegalArgumentException ignored) {
            }
            // case-insensitive fallback
            for (Object e : enumType.getEnumConstants()) {
                if (((Enum<?>) e).name().equalsIgnoreCase(name)) return e;
            }
            throw new IllegalArgumentException(
                    "No enum constant " + enumType.getName() + "." + name);
        }

        if (arg instanceof Number) {
            int ord = ((Number) arg).intValue();
            Object[] all = enumType.getEnumConstants();
            if (ord >= 0 && ord < all.length) return all[ord];
            throw new IllegalArgumentException(
                    "Enum ordinal out of range: " + ord + " for " + enumType.getName());
        }

        throw new IllegalArgumentException(
                "Cannot convert " + arg.getClass().getName() + " to enum " + enumType.getName());
    }

    public static Object instantiate(Class<?> clazz, Object... arguments) {
        try {
            // Auto-derive parameter types from arguments
            Class<?>[] parameterTypes = new Class<?>[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object arg = arguments[i];
                parameterTypes[i] =
                        (arg != null && arg.getClass().isPrimitive())
                                ? arg.getClass() // Won't really hit here often — boxing will happen
                                : (arg != null && getPrimitiveType(arg.getClass()) != null)
                                        ? getPrimitiveType(arg.getClass())
                                        : (arg != null ? arg.getClass() : Object.class);
            }

            MethodType ctorType = MethodType.methodType(void.class, parameterTypes);
            MethodHandle constructorHandle = lookup.findConstructor(clazz, ctorType);

            return constructorHandle.invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }

    public static Object enumFromExampleEnum(Class<?> exampleEnumValue, int ordinal) {
        if (exampleEnumValue == null)
            throw new IllegalArgumentException("exampleEnumValue is null");
        try {
            boolean isEnum = (boolean) invokeMethodWithAutoProjection("isEnum", exampleEnumValue);
            if (!isEnum)
                throw new IllegalArgumentException(
                        "exampleEnumValue is not an enum: " + exampleEnumValue.getName());
            Object[] constants =
                    (Object[]) invokeMethodWithAutoProjection("getEnumConstants", exampleEnumValue);
            if (constants == null)
                throw new IllegalStateException(
                        "Enum constants array is null for: " + exampleEnumValue.getName());
            if (ordinal < 0 || ordinal >= constants.length) {
                throw new IllegalArgumentException(
                        "Ordinal "
                                + ordinal
                                + " out of range 0.."
                                + (constants.length - 1)
                                + " for "
                                + exampleEnumValue.getName());
            }
            return constants[ordinal];
        } catch (Throwable e) {
            throw new RuntimeException("Failed to obtain enum from example enum value", e);
        }
    }

    public static Class<?> findFirstEnumClassWithConstantsCount(
            Object instance, int expectedCount) {
        try {
            Class<?> current = instance.getClass();
            while (current != null) {
                Object[] fields = current.getDeclaredFields();
                for (Object field : fields) {
                    try {
                        setFieldAccessibleHandle.invoke(field, true);
                        Class<?> fieldType = (Class<?>) getFieldTypeHandle.invoke(field);

                        // Must be an enum
                        boolean isEnum =
                                (boolean) invokeMethodWithAutoProjection("isEnum", fieldType);
                        if (!isEnum) continue;

                        Object constantsArr =
                                invokeMethodWithAutoProjection("getEnumConstants", fieldType);
                        Object[] constants = (Object[]) constantsArr;
                        if (constants != null && constants.length == expectedCount) {
                            return fieldType;
                        }
                    } catch (Throwable inner) {
                        // Keep scanning; mirror your existing pattern
                        inner.printStackTrace();
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to find enum class with " + expectedCount + " ordinals", e);
        }
    }

    // Helper: map boxed -> primitive types
    private static Class<?> getPrimitiveType(Class<?> boxed) {
        if (boxed == Integer.class) return int.class;
        if (boxed == Long.class) return long.class;
        if (boxed == Double.class) return double.class;
        if (boxed == Float.class) return float.class;
        if (boxed == Short.class) return short.class;
        if (boxed == Byte.class) return byte.class;
        if (boxed == Boolean.class) return boolean.class;
        if (boxed == Character.class) return char.class;
        return null;
    }

    public static Object getPrivateVariable(String fieldName, Object instanceToGetFrom) {
        try {
            Class<?> instances = instanceToGetFrom.getClass();
            while (instances != null) {
                for (Object obj : instances.getDeclaredFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        return getFieldHandle.invoke(obj, instanceToGetFrom);
                    }
                }
                for (Object obj : instances.getFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        return getFieldHandle.invoke(obj, instanceToGetFrom);
                    }
                }
                instances = instances.getSuperclass();
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    public static Object findNestedMarketApiFieldFromOutpostParams(Object instance) {
        try {
            Class<?> outerClass = instance.getClass();
            while (outerClass != null) {
                Object[] outerFields = outerClass.getDeclaredFields();
                for (Object outerField : outerFields) {
                    try {
                        // Make outer field accessible
                        setFieldAccessibleHandle.invoke(outerField, true);
                        Object innerObject = getFieldHandle.invoke(outerField, instance);
                        if (innerObject == null) continue;

                        // Get the class of the inner object
                        Class<?> innerClass = innerObject.getClass();
                        Object[] innerFields = innerClass.getDeclaredFields();

                        // Check: inner class must have exactly one field
                        if (innerFields.length == 1) {
                            Object innerField = innerFields[0];
                            Class<?> innerFieldType =
                                    (Class<?>) getFieldTypeHandle.invoke(innerField);

                            // Check if that single field is a MarketAPI
                            if (com.fs.starfarer.api.campaign.econ.MarketAPI.class.isAssignableFrom(
                                    innerFieldType)) {
                                setFieldAccessibleHandle.invoke(innerField, true);
                                return getFieldHandle.invoke(innerField, innerObject);
                            }
                        }
                    } catch (Throwable innerEx) {
                        innerEx.printStackTrace();
                    }
                }
                outerClass = outerClass.getSuperclass();
            }
            return null; // No match found
        } catch (Throwable e) {
            throw new RuntimeException("Failed to find nested MarketAPI field", e);
        }
    }

    private static boolean isMatchingConstructor(MethodType ctorType) {
        // We want 5 params exactly
        if (ctorType.parameterCount() != 5) return false;

        Class<?>[] params = ctorType.parameterArray();

        // Check first 4 parameters exactly
        if (params[0] == float.class
                && params[1] == float.class
                && params[2] == boolean.class
                && params[3] == boolean.class) {
            // We don't check params[4] because it's inaccessible, accept any class
            return true;
        }

        return false;
    }

    public static String getFloatFieldNameMatchingValue(Object instance, float targetValue) {
        try {
            Class<?> currentClass = instance.getClass();

            while (currentClass != null) {
                Object[] fields = currentClass.getDeclaredFields();

                for (Object field : fields) {
                    try {
                        // Make field accessible
                        setFieldAccessibleHandle.invoke(field, true);

                        // Check if field is float
                        Class<?> type = (Class<?>) getFieldTypeHandle.invoke(field);
                        if (type == float.class) {
                            float fieldValue = (float) getFieldHandle.invoke(field, instance);

                            if (Float.compare(fieldValue, targetValue) == 0) {
                                return (String) getFieldNameHandle.invoke(field);
                            }
                        }
                    } catch (Throwable innerEx) {
                        innerEx.printStackTrace(); // or log silently if preferred
                    }
                }

                currentClass = currentClass.getSuperclass();
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to find float field name", e);
        }

        return null; // No matching field found
    }

    public static String getStringFieldMatchingValue(Object instance, String targetValue) {
        try {
            Class<?> currentClass = instance.getClass();

            while (currentClass != null) {
                Object[] fields = currentClass.getDeclaredFields();

                for (Object field : fields) {
                    try {
                        // Make field accessible
                        setFieldAccessibleHandle.invoke(field, true);

                        // Check if field is float
                        Class<?> type = (Class<?>) getFieldTypeHandle.invoke(field);
                        if (type == String.class) {
                            String fieldValue = (String) getFieldHandle.invoke(field, instance);

                            if (targetValue.equals(fieldValue)) {
                                return (String) getFieldNameHandle.invoke(field);
                            }
                        }
                    } catch (Throwable innerEx) {
                        innerEx.printStackTrace(); // or log silently if preferred
                    }
                }

                currentClass = currentClass.getSuperclass();
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to find float field name", e);
        }

        return null; // No matching field found
    }

    public static Object getPrivateVariableFromSuperClass(
            String fieldName, Object instanceToGetFrom) {
        try {
            Class<?> instances = instanceToGetFrom.getClass();
            while (instances != null) {
                for (Object obj : instances.getDeclaredFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        return getFieldHandle.invoke(obj, instanceToGetFrom);
                    }
                }
                for (Object obj : instances.getFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        return getFieldHandle.invoke(obj, instanceToGetFrom);
                    }
                }
                instances = instances.getSuperclass();
            }
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setPrivateVariableFromSuperclass(
            String fieldName, Object instanceToModify, Object newValue) {
        try {
            Class<?> instances = instanceToModify.getClass();
            while (instances != null) {
                for (Object obj : instances.getDeclaredFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        setFieldHandle.invoke(obj, instanceToModify, newValue);
                        return;
                    }
                }
                for (Object obj : instances.getFields()) {
                    setFieldAccessibleHandle.invoke(obj, true);
                    String name = (String) getFieldNameHandle.invoke(obj);
                    if (name.equals(fieldName)) {
                        setFieldHandle.invoke(obj, instanceToModify, newValue);
                        return;
                    }
                }
                instances = instances.getSuperclass();
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasMethodOfName(String name, Object instance) {
        try {
            for (Object method : instance.getClass().getMethods()) {
                if (getMethodNameHandle.invoke(method).equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    public static Object invokeMethod(String methodName, Object instance, Object... arguments) {
        try {
            Object method = instance.getClass().getMethod(methodName);
            return invokeMethodHandle.invoke(method, instance, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeMethodDirectly(Object method, Object instance, Object... arguments) {
        try {

            return invokeMethodHandle.invoke(method, null, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static List<UIComponentAPI> getChildrenCopy(UIPanelAPI panel) {
        try {
            return (List<UIComponentAPI>) invokeMethod("getChildrenCopy", panel);
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static Pair<Object, Class<?>[]> getMethodFromSuperclass(
            String methodName, Object instance) {
        Class<?> currentClass = instance.getClass();

        while (currentClass != null) {
            // Retrieve all declared methods in the current class
            Object[] methods = currentClass.getDeclaredMethods();

            for (Object method : methods) {
                try {
                    // Retrieve the MethodHandle for the getParameterTypes method
                    MethodHandle getParameterTypesHandle =
                            ReflectionBetterUtilis.getParameterTypesHandle(
                                    method.getClass(), "getParameterTypes");
                    // Use the MethodHandle to retrieve the method's name

                    // Check if the method name matches
                    if (getMethodNameHandle.invoke(method).equals(methodName)) {
                        // Invoke the MethodHandle to get the parameter types
                        Class<?>[] parameterTypes =
                                (Class<?>[]) getParameterTypesHandle.invoke(method);
                        return new Pair<>(method, parameterTypes);
                    }
                } catch (Throwable e) {

                    e.printStackTrace(); // Handle any reflection errors
                }
            }
            // Move to the superclass if no match is found
            currentClass = currentClass.getSuperclass();
        }

        // Return null if the method was not found in the class hierarchy
        return null;
    }

    public static Object findFieldOfClass(Object instance, Class<?> fieldType) {
        try {
            Class<?> currentClass = instance.getClass();
            while (currentClass != null) {
                Object[] fields = currentClass.getDeclaredFields();
                for (Object field : fields) {
                    try {
                        Class<?> type = (Class<?>) getFieldTypeHandle.invoke(field);
                        if (fieldType.isAssignableFrom(type)) {
                            setFieldAccessibleHandle.invoke(field, true);
                            return getFieldHandle.invoke(field, instance);
                        }
                    } catch (Throwable inner) {
                        inner.printStackTrace();
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return null;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to find field of type " + fieldType.getName(), e);
        }
    }

    public static Object invokeStaticMethodWithAutoProjection(
            Class<?> targetClass, String methodName, Object... arguments) {
        try {
            ResolvedMethod resolved = resolveBestMethod(targetClass, methodName, true, arguments);
            if (resolved == null) {
                throw new NoSuchMethodException(
                        "Static method "
                                + methodName
                                + " not found in class hierarchy of "
                                + targetClass.getName()
                                + " for "
                                + ((arguments == null) ? 0 : arguments.length)
                                + " args");
            }

            setMethodAccessable.invoke(resolved.method, true);
            return invokeMethodHandle.invoke(resolved.method, null, resolved.projectedArguments);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                Throwable cause = ((InvocationTargetException) e).getTargetException();
                System.err.println(
                        "Root cause of InvocationTargetException: " + cause.getClass().getName());
                cause.printStackTrace();
            } else {
                e.printStackTrace();
            }
            throw new RuntimeException(e);
        }
    }

    public static String findFieldWithMatchingCtor(Object instance) {
        try {
            Class<?> currentClass = instance.getClass();

            while (currentClass != null) {
                Object[] fields = currentClass.getDeclaredFields();

                for (Object field : fields) {
                    try {
                        setFieldAccessibleHandle.invoke(field, true);
                        Object fieldValue = getFieldHandle.invoke(field, instance);
                        if (fieldValue == null) continue;

                        Class<?> fieldType = (Class<?>) getFieldTypeHandle.invoke(field);
                        if (fieldType.getConstructors().length != 0) {
                            Object[] constructors =
                                    (Object[]) invokeMethod("getConstructors", fieldType);
                            Class<?>[] classes =
                                    (Class<?>[]) invokeMethod("getParameterTypes", constructors[0]);
                            if (classes.length == 5) {
                                if (classes[0] == float.class
                                        && classes[1] == float.class
                                        && classes[2] == boolean.class
                                        && classes[3] == boolean.class) {
                                    return (String) getFieldNameHandle.invoke(field);
                                }
                            }
                        }

                    } catch (Throwable inner) {
                        inner.printStackTrace();
                    }
                }

                currentClass = currentClass.getSuperclass();
            }

        } catch (Throwable e) {
            throw new RuntimeException("Failed to find matching constructor field", e);
        }

        return null;
    }

    private static final int MODIFIER_STATIC = 0x0008;
    private static final int METHOD_SCORE_IMPOSSIBLE = Integer.MAX_VALUE / 4;

    private static final class ResolvedMethod {
        final Object method;
        final Class<?>[] parameterTypes;
        final Object[] projectedArguments;
        final int score;

        private ResolvedMethod(
                Object method, Class<?>[] parameterTypes, Object[] projectedArguments, int score) {
            this.method = method;
            this.parameterTypes = parameterTypes;
            this.projectedArguments = projectedArguments;
            this.score = score;
        }
    }

    /**
     * Finds the best overload instead of returning the first method that has this name. Lower score
     * wins: - exact type / primitive-box match - widening numeric conversion - assignable reference
     * type - safe auto conversions from convertArgumentAuto(...) - varargs as fallback
     */
    private static ResolvedMethod resolveBestMethod(
            Class<?> startClass, String methodName, boolean requireStatic, Object... arguments) {
        if (arguments == null) arguments = new Object[0];

        ResolvedMethod best = null;
        Class<?> currentClass = startClass;
        int classDepth = 0;

        while (currentClass != null) {
            Object[] methods = currentClass.getDeclaredMethods();

            for (Object method : methods) {
                try {
                    String currentName = (String) getMethodNameHandle.invoke(method);
                    if (!methodName.equals(currentName)) continue;

                    int modifiers = (int) getModifiersHandle.invoke(method);
                    boolean isStatic = (modifiers & MODIFIER_STATIC) != 0;
                    if (requireStatic != isStatic) continue;

                    Class<?>[] parameterTypes = (Class<?>[]) getParameterTypesHandle.invoke(method);
                    boolean varArgs = (boolean) isMethodVarArgsHandle.invoke(method);

                    Object[] projectedArgs = projectMethodArgs(arguments, parameterTypes, varArgs);
                    if (projectedArgs == null) continue;

                    int score = scoreMethodArguments(arguments, parameterTypes, varArgs);
                    if (score >= METHOD_SCORE_IMPOSSIBLE) continue;

                    // Prefer methods declared lower in the inheritance tree. Prefer fixed arity
                    // over varargs.
                    score += classDepth * 10;
                    if (varArgs) score += 25;

                    if (best == null || score < best.score) {
                        best = new ResolvedMethod(method, parameterTypes, projectedArgs, score);
                    }
                } catch (Throwable inner) {
                    inner.printStackTrace();
                }
            }

            currentClass = currentClass.getSuperclass();
            classDepth++;
        }

        return best;
    }

    private static Object[] projectMethodArgs(
            Object[] arguments, Class<?>[] parameterTypes, boolean varArgs) {
        try {
            if (arguments == null) arguments = new Object[0];

            if (!varArgs) {
                if (parameterTypes.length != arguments.length) return null;

                Object[] projected = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    projected[i] = convertArgumentAuto(arguments[i], parameterTypes[i]);
                }
                return projected;
            }

            if (parameterTypes.length == 0) {
                return arguments.length == 0 ? new Object[0] : null;
            }

            int fixedCount = parameterTypes.length - 1;
            if (arguments.length < fixedCount) return null;

            Object[] projected = new Object[parameterTypes.length];
            for (int i = 0; i < fixedCount; i++) {
                projected[i] = convertArgumentAuto(arguments[i], parameterTypes[i]);
            }

            Class<?> varArrayType = parameterTypes[fixedCount];
            Class<?> varComponentType = varArrayType.getComponentType();
            if (varComponentType == null) return null;

            int varCount = arguments.length - fixedCount;

            // Caller already supplied the whole vararg array.
            if (varCount == 1
                    && arguments[fixedCount] != null
                    && varArrayType.isInstance(arguments[fixedCount])) {
                projected[fixedCount] = arguments[fixedCount];
                return projected;
            }

            Object varArray = java.lang.reflect.Array.newInstance(varComponentType, varCount);
            for (int i = 0; i < varCount; i++) {
                Object converted = convertArgumentAuto(arguments[fixedCount + i], varComponentType);
                java.lang.reflect.Array.set(varArray, i, converted);
            }
            projected[fixedCount] = varArray;
            return projected;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int scoreMethodArguments(
            Object[] arguments, Class<?>[] parameterTypes, boolean varArgs) {
        if (arguments == null) arguments = new Object[0];

        if (!varArgs) {
            if (parameterTypes.length != arguments.length) return METHOD_SCORE_IMPOSSIBLE;

            int score = 0;
            for (int i = 0; i < parameterTypes.length; i++) {
                int singleScore = scoreSingleArgument(arguments[i], parameterTypes[i]);
                if (singleScore >= METHOD_SCORE_IMPOSSIBLE) return METHOD_SCORE_IMPOSSIBLE;
                score += singleScore;
            }
            return score;
        }

        if (parameterTypes.length == 0) return arguments.length == 0 ? 0 : METHOD_SCORE_IMPOSSIBLE;

        int fixedCount = parameterTypes.length - 1;
        if (arguments.length < fixedCount) return METHOD_SCORE_IMPOSSIBLE;

        int score = 0;
        for (int i = 0; i < fixedCount; i++) {
            int singleScore = scoreSingleArgument(arguments[i], parameterTypes[i]);
            if (singleScore >= METHOD_SCORE_IMPOSSIBLE) return METHOD_SCORE_IMPOSSIBLE;
            score += singleScore;
        }

        Class<?> varArrayType = parameterTypes[fixedCount];
        Class<?> varComponentType = varArrayType.getComponentType();
        if (varComponentType == null) return METHOD_SCORE_IMPOSSIBLE;

        int varCount = arguments.length - fixedCount;
        if (varCount == 1
                && arguments[fixedCount] != null
                && varArrayType.isInstance(arguments[fixedCount])) {
            return score + scoreSingleArgument(arguments[fixedCount], varArrayType);
        }

        for (int i = 0; i < varCount; i++) {
            int singleScore = scoreSingleArgument(arguments[fixedCount + i], varComponentType);
            if (singleScore >= METHOD_SCORE_IMPOSSIBLE) return METHOD_SCORE_IMPOSSIBLE;
            score += singleScore + 2;
        }

        return score;
    }

    private static int scoreSingleArgument(Object arg, Class<?> targetType) {
        if (arg == null) {
            if (targetType.isPrimitive()) return METHOD_SCORE_IMPOSSIBLE;
            return targetType == Object.class ? 120 : 100;
        }

        Class<?> argType = arg.getClass();
        Class<?> boxedTarget = boxType(targetType);

        if (boxedTarget == argType) return 0;
        if (targetType.isAssignableFrom(argType))
            return 10 + inheritanceDistance(argType, targetType);

        if (isNumericType(boxedTarget) && arg instanceof Number) {
            int argRank = numericRank(argType);
            int targetRank = numericRank(boxedTarget);
            if (argRank >= 0 && targetRank >= 0) {
                if (targetRank >= argRank) return 5 + (targetRank - argRank); // widening
                return 40 + (argRank - targetRank); // narrowing, allowed but worse
            }
            return 45;
        }

        if ((boxedTarget == Boolean.class && arg instanceof Boolean)
                || (boxedTarget == Character.class && arg instanceof Character)) {
            return 0;
        }

        if ((boxedTarget == Boolean.class || boxedTarget == Character.class)
                && arg instanceof CharSequence) {
            return 70;
        }

        if (targetType == String.class || CharSequence.class.isAssignableFrom(targetType)) {
            return 60;
        }

        if (targetType.isEnum() && (arg instanceof CharSequence || arg instanceof Number)) {
            return 75;
        }

        if (fileclass != null
                && fileclass.isAssignableFrom(targetType)
                && arg instanceof CharSequence) {
            return 80;
        }

        try {
            convertArgumentAuto(arg, targetType);
            return 95;
        } catch (Throwable ignored) {
            return METHOD_SCORE_IMPOSSIBLE;
        }
    }

    private static int inheritanceDistance(Class<?> source, Class<?> target) {
        if (source == null || target == null) return 50;
        if (source.equals(target)) return 0;

        if (target.isInterface()) {
            return implementsInterface(source, target) ? 5 : 50;
        }

        int distance = 0;
        Class<?> current = source;
        while (current != null) {
            if (current.equals(target)) return distance;
            current = current.getSuperclass();
            distance++;
        }
        return 50;
    }

    private static boolean implementsInterface(Class<?> source, Class<?> targetInterface) {
        if (source == null || targetInterface == null) return false;
        for (Class<?> iface : source.getInterfaces()) {
            if (iface.equals(targetInterface) || implementsInterface(iface, targetInterface))
                return true;
        }
        return implementsInterface(source.getSuperclass(), targetInterface);
    }

    private static Class<?> boxType(Class<?> type) {
        if (type == null || !type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == void.class) return Void.class;
        return type;
    }

    private static boolean isNumericType(Class<?> type) {
        return type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class;
    }

    private static int numericRank(Class<?> type) {
        Class<?> boxed = boxType(type);
        if (boxed == Byte.class) return 1;
        if (boxed == Short.class) return 2;
        if (boxed == Integer.class) return 3;
        if (boxed == Long.class) return 4;
        if (boxed == Float.class) return 5;
        if (boxed == Double.class) return 6;
        return -1;
    }

    public static Object invokeMethodWithAutoProjection(
            String methodName, Object instance, Object... arguments) {
        if (instance == null) {
            throw new IllegalArgumentException(
                    "Cannot invoke method " + methodName + " on null instance");
        }

        try {
            ResolvedMethod resolved =
                    resolveBestMethod(instance.getClass(), methodName, false, arguments);
            if (resolved == null) {
                throw new NoSuchMethodException(
                        "Method "
                                + methodName
                                + " not found in class hierarchy of "
                                + instance.getClass().getName()
                                + " for "
                                + ((arguments == null) ? 0 : arguments.length)
                                + " args");
            }

            setMethodAccessable.invoke(resolved.method, true);
            return invokeMethodHandle.invoke(
                    resolved.method, instance, resolved.projectedArguments);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                Throwable cause = ((InvocationTargetException) e).getTargetException();
                System.err.println(
                        "Root cause of InvocationTargetException: " + cause.getClass().getName());
                cause.printStackTrace();
            } else {
                e.printStackTrace();
            }
            throw new RuntimeException(e);
        }
    }

    // Helper function to convert an argument to the expected type
    public static Object convertArgument(Object arg, Class<?> targetType) {
        if (arg == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException(
                        "null cannot be converted to primitive " + targetType.getName());
            }
            return null;
        }

        if (targetType.isAssignableFrom(arg.getClass())) {
            return arg; // Use as-is if types match
        }

        Class<?> boxedTarget = boxType(targetType);

        if (boxedTarget == Integer.class) {
            return ((Number) arg).intValue();
        } else if (boxedTarget == Long.class) {
            return ((Number) arg).longValue();
        } else if (boxedTarget == Double.class) {
            return ((Number) arg).doubleValue();
        } else if (boxedTarget == Float.class) {
            return ((Number) arg).floatValue();
        } else if (boxedTarget == Short.class) {
            return ((Number) arg).shortValue();
        } else if (boxedTarget == Byte.class) {
            return ((Number) arg).byteValue();
        } else if (boxedTarget == Boolean.class) {
            if (arg instanceof Boolean) return arg;
            throw new IllegalArgumentException(
                    "Cannot convert " + arg.getClass().getName() + " to boolean");
        } else if (boxedTarget == Character.class) {
            if (arg instanceof Character) return arg;
            throw new IllegalArgumentException(
                    "Cannot convert " + arg.getClass().getName() + " to char");
        }

        return targetType.cast(arg);
    }

    public static Object invokeStaticMethod(
            Class<?> targetClass, String methodName, Object... arguments) {
        try {
            // Retrieve the parameter types of the arguments
            Class<?>[] parameterTypes = new Class[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameterTypes[i] = arguments[i].getClass();
            }

            // Find the method by its name and parameter types
            Object method = findStaticMethodByParameterTypes(targetClass, parameterTypes);
            if (method == null) {
                throw new NoSuchMethodException(
                        "Static method "
                                + methodName
                                + " not found in class "
                                + targetClass.getName());
            }

            // Invoke the method (static methods do not need an instance)
            return invokeMethodHandle.invoke(method, null, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object findFieldByType(Object targetObject, Class<?> fieldType) {
        try {
            Class<?> currentClass = targetObject.getClass();

            while (currentClass != null) {
                // Retrieve all declared fields dynamically
                Object[] fields = currentClass.getDeclaredFields();

                for (Object field : fields) {
                    try {
                        // Retrieve field type dynamically
                        Class<?> fieldClass =
                                (Class<?>) invokeMethodWithAutoProjection("getType", field);

                        // Check if the field type matches or is assignable
                        if (fieldType.isAssignableFrom(fieldClass)) {
                            setFieldAccessibleHandle.invoke(field, true);
                            return getFieldHandle.invoke(field, targetObject);
                        }
                    } catch (Throwable e) {
                        // Handle exceptions gracefully during field inspection
                        e.printStackTrace();
                    }
                }

                // Move to the superclass dynamically
                currentClass = currentClass.getSuperclass();
            }

            // Return null if no matching field is found
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object findStaticMethodByParameterTypes(
            Class<?> targetClass, Class<?>... parameterTypes) {
        try {
            Class<?> currentClass = targetClass;

            while (currentClass != null) {
                // Retrieve all declared methods dynamically
                Object[] methods = currentClass.getDeclaredMethods();

                for (Object method : methods) {
                    try {
                        // Retrieve method modifiers dynamically
                        int modifiers = (int) getModifiersHandle.invoke(method);

                        // Check if the method is static
                        if ((modifiers & 0x0008) != 0) { // 0x0008 is the `static` modifier bit
                            // Retrieve parameter types dynamically
                            Class<?>[] methodParamTypes =
                                    (Class<?>[]) getParameterTypesHandle.invoke(method);

                            // Compare parameter types
                            if (areParameterTypesMatching(methodParamTypes, parameterTypes)) {
                                return method; // Return the matching method
                            }
                        }
                    } catch (Throwable e) {
                        // Handle exceptions gracefully during method inspection
                        e.printStackTrace();
                    }
                }

                // Move to the superclass dynamically
                currentClass = currentClass.getSuperclass();
            }

            // Return null if no matching method is found
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean replaceFirstStringFieldWithValue(
            Object instance, String targetValue, String newValue, boolean matchNulls) {
        if (instance == null) return false;

        try {
            Class<?> currentClass = instance.getClass();

            while (currentClass != null) {
                Object[] fields = currentClass.getDeclaredFields();

                for (Object field : fields) {
                    try {
                        setFieldAccessibleHandle.invoke(field, true);

                        Class<?> type = (Class<?>) getFieldTypeHandle.invoke(field);
                        if (type != String.class) continue;

                        String current = (String) getFieldHandle.invoke(field, instance);

                        boolean match;
                        if (targetValue == null) match = matchNulls && current == null;
                        else match = targetValue.equals(current);

                        if (!match) continue;

                        setFieldHandle.invoke(field, instance, newValue);
                        return true;
                    } catch (Throwable inner) {
                        inner.printStackTrace();
                    }
                }

                currentClass = currentClass.getSuperclass();
            }

            return false;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to replace String field value", e);
        }
    }

    // Helper function to compare parameter types
    private static boolean areParameterTypesMatching(
            Class<?>[] methodParamTypes, Class<?>[] targetParamTypes) {
        if (methodParamTypes.length != targetParamTypes.length) {
            return false;
        }

        for (int i = 0; i < methodParamTypes.length; i++) {
            if (!methodParamTypes[i].isAssignableFrom(targetParamTypes[i])) {
                return false;
            }
        }

        return true;
    }

    public static List<UIComponentAPI> getChildren(UIPanelAPI panelAPI) {
        return ReflectionUtilis.getChildrenCopy(panelAPI);
    }
}
