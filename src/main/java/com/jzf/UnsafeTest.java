package com.jzf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Unsafe类的使用说明
 * 1.获取,修改对象字段或静态字段的值
 * 2.直接读取,修改指定内存的值
 * <p>
 * https://my.oschina.net/editorial-story/blog/3019773?from=20190331
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/6/24 11:20:12
 */
public class UnsafeTest {
    private static final Logger logger = LoggerFactory.getLogger(UnsafeTest.class);

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);

        logger.info("---object field---");
        Field field_name = Class1.class.getDeclaredField("name");
        long offset_name = unsafe.objectFieldOffset(field_name);//获取实例字段的偏移量.
        logger.info("offset_name: {}", offset_name);
        Class1 class1 = new Class1("jzf");
        String name = (String) unsafe.getObject(class1, offset_name);//获取实例字段的值.
        logger.info("修改前的值: {}", name);
        unsafe.putObject(class1, offset_name, "jzf2");//修改实例字段的值.
        name = (String) unsafe.getObject(class1, offset_name);//获取实例字段的值.
        logger.info("修改后的值: {}", name);

        logger.info("---class field---");
        Field field_age = Class1.class.getDeclaredField("age");
        Object fieldBase = unsafe.staticFieldBase(field_age);//获取静态字段的基地址.
        logger.info("fieldBase: {},是否等于Class1.class: {}", fieldBase, fieldBase == Class1.class);
        long offset_age = unsafe.staticFieldOffset(field_age);//获取静态字段的偏移量.
        logger.info("offset_age: {}", offset_age);
        int age = unsafe.getInt(fieldBase, offset_age);//获取静态字段的值(或者使用unsafe.getInt(Class1.class, offset_age)).
        logger.info("修改前的值: {}", age);
        unsafe.putInt(fieldBase, offset_age, 18);//修改静态字段的值.
        age = unsafe.getInt(Class1.class, offset_age);//获取静态字段的值(或者使用unsafe.getInt(Class1.class, offset_age)).
        logger.info("修改后的值: {}", age);

        logger.info("---array[i] field---");
        Class1[] class1s = new Class1[]{new Class1("feng1"), new Class1("feng2")};
        int arrayBaseOffset = unsafe.arrayBaseOffset(class1s.getClass());//获取数组第一个元素的偏移量.
        logger.info("arrayBaseOffset: {}", arrayBaseOffset);
        int arrayIndexScale = unsafe.arrayIndexScale(class1s.getClass());//获取数组中元素所占字节.
        logger.info("arrayIndexScale: {}", arrayIndexScale);
        Class1 class11 = (Class1) unsafe.getObject(class1s, (long) arrayBaseOffset + 0 * arrayIndexScale);//获取数组中指定元素的值.
        logger.info("修改前的值: {}", class11.toString());
        unsafe.putObject(class1s, (long) arrayBaseOffset + 0 * arrayIndexScale, new Class1("feng3"));//修改数组中指定元素的值.
        class11 = (Class1) unsafe.getObject(class1s, (long) arrayBaseOffset + 0 * arrayIndexScale);//获取数组中指定元素的值.
        logger.info("修改后的值: {}", class11.toString());

        int[] ints = {3, 2, 53, 65, 23};
        int anInt = unsafe.getInt(ints, (long) Unsafe.ARRAY_INT_BASE_OFFSET + 3 * Unsafe.ARRAY_INT_INDEX_SCALE);//获取数组中指定元素的值.
        logger.info("修改前的值: {}", anInt);
        unsafe.putInt(ints, (long) Unsafe.ARRAY_INT_BASE_OFFSET + 3 * Unsafe.ARRAY_INT_INDEX_SCALE, 999);//修改数组中指定元素的值.
        anInt = unsafe.getInt(ints, (long) Unsafe.ARRAY_INT_BASE_OFFSET + 3 * Unsafe.ARRAY_INT_INDEX_SCALE);//获取数组中指定元素的值.
        logger.info("修改后的值: {}", anInt);

        logger.info("---allocate/free Memory---");
        long address = unsafe.allocateMemory(8);//申请4个字节内存.
        unsafe.setMemory(address, 8, (byte) 0);//初始化指定地址内存的值.
        unsafe.putLong(address, 0x12345678);//设置指定内存的值
        long value = unsafe.getLong(address);//读取指定地址内存的值.
        logger.info("读取分配的内存的值: 0x{}", Long.toHexString(value));
        unsafe.freeMemory(address);//释放指定地址的内存

        logger.info("---class initialized---");

        Field f1 = MyObj.class.getDeclaredField("f1");
        long f1Offset = unsafe.staticFieldOffset(f1);
        int f1Val = unsafe.getInt(MyObj.class, f1Offset);
        logger.info("class类初始化之前static field: {}", f1Val);//当前f1静态字段只加载到内存,还没有经过链接阶段,所以f1的值为默认值
        logger.info("class类是否需要初始化: {}", unsafe.shouldBeInitialized(MyObj.class));//就是检查链接阶段有没有执行(包括初始化静态字段和静态块).
        unsafe.ensureClassInitialized(MyObj.class);//确保class类初始化(初始化静态字段和静态块).
        f1Val = unsafe.getInt(MyObj.class, f1Offset);
        logger.info("class类初始化之后static field: {}", f1Val);//链接阶段执行后,f1的值被赋值为2.

        Field f2 = MyObj.class.getDeclaredField("f2");
        long f2Offset = unsafe.staticFieldOffset(f2);
        int f2Val = unsafe.getInt(MyObj.class, f2Offset);
        logger.info("静态常量的值在编译器已经赋值,跟class类初始化无关: {}", f2Val);//f2为静态常量,所以在编译期已经赋值,所以值为1.

        logger.info("---CAS---");
        logger.info("field等于1的话就修改: {}", unsafe.compareAndSwapInt(MyObj.class, f1Offset, 1, 111));
        logger.info("field等于11的话就修改: {}", unsafe.compareAndSwapInt(MyObj.class, f1Offset, 11, 111));

        logger.info("---system load average---");
        double[] loadavg = new double[1];
        if (unsafe.getLoadAverage(loadavg, 1) == 1) {
            logger.info("系统1分钟内的负载: {}", loadavg[0]);
        } else {
            logger.info("系统1分钟内的负载: {}", -1);
        }

        logger.info("---Unsafe Constant---");
        logger.info("ARRAY_BOOLEAN_BASE_OFFSET: {}", Unsafe.ARRAY_BOOLEAN_BASE_OFFSET);
        logger.info("ARRAY_BYTE_BASE_OFFSET: {}", Unsafe.ARRAY_BYTE_BASE_OFFSET);
        logger.info("ARRAY_SHORT_BASE_OFFSET: {}", Unsafe.ARRAY_SHORT_BASE_OFFSET);
        logger.info("ARRAY_CHAR_BASE_OFFSET: {}", Unsafe.ARRAY_CHAR_BASE_OFFSET);
        logger.info("ARRAY_INT_BASE_OFFSET: {}", Unsafe.ARRAY_INT_BASE_OFFSET);
        logger.info("ARRAY_LONG_BASE_OFFSET: {}", Unsafe.ARRAY_LONG_BASE_OFFSET);
        logger.info("ARRAY_FLOAT_BASE_OFFSET: {}", Unsafe.ARRAY_FLOAT_BASE_OFFSET);
        logger.info("ARRAY_DOUBLE_BASE_OFFSET: {}", Unsafe.ARRAY_DOUBLE_BASE_OFFSET);
        logger.info("ARRAY_OBJECT_BASE_OFFSET: {}", Unsafe.ARRAY_OBJECT_BASE_OFFSET);
        logger.info("ARRAY_BOOLEAN_INDEX_SCALE: {}", Unsafe.ARRAY_BOOLEAN_INDEX_SCALE);
        logger.info("ARRAY_BYTE_INDEX_SCALE: {}", Unsafe.ARRAY_BYTE_INDEX_SCALE);
        logger.info("ARRAY_SHORT_INDEX_SCALE: {}", Unsafe.ARRAY_SHORT_INDEX_SCALE);
        logger.info("ARRAY_CHAR_INDEX_SCALE: {}", Unsafe.ARRAY_CHAR_INDEX_SCALE);
        logger.info("ARRAY_INT_INDEX_SCALE: {}", Unsafe.ARRAY_INT_INDEX_SCALE);
        logger.info("ARRAY_LONG_INDEX_SCALE: {}", Unsafe.ARRAY_LONG_INDEX_SCALE);
        logger.info("ARRAY_FLOAT_INDEX_SCALE: {}", Unsafe.ARRAY_FLOAT_INDEX_SCALE);
        logger.info("ARRAY_DOUBLE_INDEX_SCALE: {}", Unsafe.ARRAY_DOUBLE_INDEX_SCALE);
        logger.info("ARRAY_OBJECT_INDEX_SCALE: {}", Unsafe.ARRAY_OBJECT_INDEX_SCALE);
        logger.info("ADDRESS_SIZE: {}", Unsafe.ADDRESS_SIZE);
        logger.info("addressSize(): {}", unsafe.addressSize());
        logger.info("pageSize(): {}", unsafe.pageSize());
    }

    static class Class1 {
        String name;
        static int age = 25;

        public Class1(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Class1{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    static class MyObj {
        static int f1 = 1;
        final static int f2 = 22;

        static {
            f1 = 11;
            System.out.println("MyObj init");
        }
    }
}