package com.EOT.project.controller;
import com.EOT.project.service.UserService; import org.junit.jupiter.api.*; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension; import static org.assertj.core.api.Assertions.assertThat; import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class) class UserControllerTest { @Mock UserService service; @Mock com.EOT.project.service.CurrentUserService current; @InjectMocks UserController controller;
 @Test void exposesUsernameAvailabilityWithoutAuthentication() { when(service.isUsernameAvailable("new_user")).thenReturn(true); assertThat(controller.usernameAvailability("new_user").available()).isTrue(); } }
