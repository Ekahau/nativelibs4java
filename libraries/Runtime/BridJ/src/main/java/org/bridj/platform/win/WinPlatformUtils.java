package org.bridj.platform.win;
import org.bridj.platform.*;
import org.bridj.*;
import static org.bridj.Pointer.*;

public class WinPlatformUtils extends PlatformUtils {
	public long getIdleTimeMillis() {
		User32.LASTINPUTINFO lastInputInfo = new User32.LASTINPUTINFO();
		if (!User32.GetLastInputInfo(pointerTo(lastInputInfo)))
			throw new RuntimeException("Failed to get last input info !");
		
		return Kernel32.GetTickCount() - lastInputInfo.dwTime();
	}
}
