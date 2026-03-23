import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.PortalForcer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DumpMethods {
    public static void main(String[] args) {
        System.out.println("=== Entity ===");
        for (Method m : Entity.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("portal")) {
                System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName());
            }
        }
        System.out.println("\n=== NetherPortalBlock ===");
        for (Method m : NetherPortalBlock.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("portal")) {
                System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName());
            }
        }
        System.out.println("\n=== PortalForcer ===");
        for (Method m : PortalForcer.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("portal")) {
                System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName());
            }
        }
    }
}
