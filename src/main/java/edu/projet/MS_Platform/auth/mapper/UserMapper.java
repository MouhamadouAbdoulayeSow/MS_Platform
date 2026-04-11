// Package: edu.projet.MS_Platform.auth.mapper
package edu.projet.MS_Platform.auth.mapper;

import edu.projet.MS_Platform.auth.dto.UserProfileResponse;
import edu.projet.MS_Platform.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    
    UserProfileResponse toProfileResponse(User user);
    
    List<UserProfileResponse> toProfileResponseList(List<User> users);
}
