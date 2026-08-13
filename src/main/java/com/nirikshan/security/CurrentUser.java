package com.nirikshan.security;
import com.nirikshan.model.User;
import com.nirikshan.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
@Component public class CurrentUser { private final UserRepository users; public CurrentUser(UserRepository users){this.users=users;} public User get(){Authentication a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();User user=users.findByEmailIgnoreCase(a.getName()).orElseThrow(()->new IllegalStateException("Authenticated user no longer exists"));if(user.getRole()==com.nirikshan.model.UserRole.ADMIN&&!user.isProtectedAdmin()){User first=users.findFirstByRoleOrderByCreatedAtAsc(com.nirikshan.model.UserRole.ADMIN).orElse(null);if(first!=null&&first.getId().equals(user.getId())){user.setProtectedAdmin(true);users.save(user);}}return user;} }
