#include <string>

#include "framework.h"
#include "kagerou-screenshot.h"

#include "scoped-cleanup.hpp"

using namespace std::literals;

bool message_on_false(bool result, const wchar_t* function) {
	if (result) return false;
	
	MessageBox(nullptr, (function + L" failed"s).c_str(), L"kagerou-screenshot", MB_OK);
}

bool gle_on_false(bool result, const wchar_t* function) {
	if (result) return false;

	auto err = GetLastError();
	MessageBox(nullptr, (function + L" failed ("s + std::to_wstring(err) + L")").c_str(), L"kagerou-screenshot", MB_OK);
	return true;
}

#define GLE_AND_RETURN_ON_FALSE(result, function, ret) do {if (gle_on_false((result), (function))) return ret;} while (false)
#define MESSAGE_AND_RETURN_ON_FALSE(result, function, ret) do {if (message_on_false((result), (function))) return ret;} while (false)
#define MESSAGE_AND_RETURN_ON_FALSE_OR_GDI(result, function, ret) MESSAGE_AND_RETURN_ON_FALSE((result) and (result) != HGDI_ERROR, (function), ret)
#define RETURN_ON_FALSE(result, ret) do {if (not (result)) return ret;} while (false)

auto create_bitmap() -> HBITMAP {
	auto window = FindWindow(nullptr, L"kagerou");
	GLE_AND_RETURN_ON_FALSE(window, L"FindWindow", nullptr);

	auto screen_dc = GetDC(nullptr);
	MESSAGE_AND_RETURN_ON_FALSE(screen_dc, L"GetWindowDC", nullptr);
	ScopedCleanup cleanup_dc([window, screen_dc] { ReleaseDC(window, screen_dc); });

	auto ss_dc = CreateCompatibleDC(screen_dc);
	MESSAGE_AND_RETURN_ON_FALSE(ss_dc, L"CreateCompatibleDC", nullptr);
	ScopedCleanup cleanup_ss_dc([ss_dc] { DeleteDC(ss_dc); });

	RECT window_dims;
	GLE_AND_RETURN_ON_FALSE(GetWindowRect(window, &window_dims), L"GetWindowRect", nullptr);

	auto width = window_dims.right - window_dims.left;
	auto height = window_dims.bottom - window_dims.top;

	auto ss_bitmap = CreateCompatibleBitmap(screen_dc, width, height);
	MESSAGE_AND_RETURN_ON_FALSE(ss_bitmap, L"CreateCompatibleBitmap", nullptr);

	auto old_bitmap = SelectObject(ss_dc, ss_bitmap);
	MESSAGE_AND_RETURN_ON_FALSE_OR_GDI(old_bitmap, L"SelectObject", nullptr);
	ScopedCleanup cleanup_old_bitmap([ss_dc, old_bitmap] { SelectObject(ss_dc, old_bitmap); });

	GLE_AND_RETURN_ON_FALSE(BitBlt(ss_dc, 0, 0, width, height, screen_dc, window_dims.left, window_dims.top, SRCCOPY), L"BitBlt", nullptr);

	return ss_bitmap;
}

void screenshot_kagerou(HWND own_window) {
	auto ss_bitmap = create_bitmap();
	RETURN_ON_FALSE(ss_bitmap,);
	ScopedCleanup cleanup_ss_bitmap([ss_bitmap] { DeleteObject(ss_bitmap); });

	GLE_AND_RETURN_ON_FALSE(OpenClipboard(own_window), L"OpenClipboard",);
	ScopedCleanup close_clipboard([] { CloseClipboard(); });
	
	GLE_AND_RETURN_ON_FALSE(EmptyClipboard(), L"EmptyClipboard",);
	GLE_AND_RETURN_ON_FALSE(SetClipboardData(CF_BITMAP, ss_bitmap), L"SetClipboardData",);
}

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE, LPWSTR, int) {
	GLE_AND_RETURN_ON_FALSE(RegisterHotKey(nullptr, 1, MOD_SHIFT, VK_PAUSE), L"RegisterHotKey", 1);

	auto window = CreateWindowEx(0, L"STATIC", nullptr, 0, 0, 0, 0, 0, HWND_MESSAGE, nullptr, nullptr, nullptr);
	GLE_AND_RETURN_ON_FALSE(window, L"CreateWindowEx", 1);

	MSG msg;
	while (GetMessage(&msg, nullptr, 0, 0) > 0) {
		TranslateMessage(&msg);
		DispatchMessage(&msg);

		if (msg.message == WM_HOTKEY) {
			screenshot_kagerou(window);
		}
	}

	return 0;
}